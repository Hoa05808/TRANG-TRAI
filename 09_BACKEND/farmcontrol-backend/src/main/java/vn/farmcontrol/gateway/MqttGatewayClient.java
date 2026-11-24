package vn.farmcontrol.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.farmcontrol.config.FarmProperties;
import vn.farmcontrol.gateway.GatewayEvents.*;
import vn.farmcontrol.plc.TelemetryMessage;

/**
 * Kết nối MQTT broker (Mosquitto). Topic:
 *   farm/{farmId}/telemetry        ESP32 -> backend  (QoS 0)
 *   farm/{farmId}/command/ack      ESP32 -> backend  (QoS 1)
 *   farm/{farmId}/gateway/status   ESP32 -> backend  ("online"/"offline", retained, Last Will)
 *   farm/{farmId}/command          backend -> ESP32  (QoS 1, KHÔNG retained)
 */
@Component
@ConditionalOnProperty(name = "farm.gateway.mode", havingValue = "mqtt")
public class MqttGatewayClient implements GatewayClient, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MqttGatewayClient.class);

    private final FarmProperties props;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;
    private final String topicTelemetry;
    private final String topicAck;
    private final String topicStatus;
    private final String topicCommand;

    private MqttAsyncClient client;
    private volatile boolean running;

    public MqttGatewayClient(FarmProperties props, ApplicationEventPublisher events, ObjectMapper json) {
        this.props = props;
        this.events = events;
        this.json = json;
        String base = "farm/" + props.farmId();
        this.topicTelemetry = base + "/telemetry";
        this.topicAck = base + "/command/ack";
        this.topicStatus = base + "/gateway/status";
        this.topicCommand = base + "/command";
    }

    @Override
    public void start() {
        try {
            FarmProperties.Mqtt cfg = props.mqtt();
            client = new MqttAsyncClient(cfg.url(), cfg.clientId(), new MemoryPersistence());
            client.setCallback(new Callback());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setKeepAliveInterval(30);
            options.setConnectionTimeout(10);
            if (cfg.username() != null && !cfg.username().isBlank()) {
                options.setUserName(cfg.username());
                options.setPassword(cfg.password() == null ? new char[0] : cfg.password().toCharArray());
            }
            if (!cfg.url().startsWith("ssl://")) {
                log.warn("MQTT đang dùng kết nối KHÔNG mã hóa ({}). Khi triển khai thật hãy dùng ssl://...:8883", cfg.url());
            }
            running = true;
            connect(options);
        } catch (MqttException e) {
            throw new IllegalStateException("Không khởi tạo được MQTT client: " + e.getMessage(), e);
        }
    }

    private void connect(MqttConnectOptions options) throws MqttException {
        client.connect(options, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken token) {
                // Việc subscribe nằm trong connectComplete để chạy lại sau mỗi lần tự kết nối lại
            }

            @Override
            public void onFailure(IMqttToken token, Throwable ex) {
                log.warn("Kết nối MQTT broker {} thất bại: {}. Thử lại sau 10 giây",
                        props.mqtt().url(), ex.getMessage());
                events.publishEvent(new BrokerConnectionChanged(false, ex.getMessage()));
                // Lần kết nối đầu tiên thất bại thì Paho không tự thử lại -> tự lên lịch
                Thread.ofVirtual().start(() -> {
                    try {
                        Thread.sleep(10_000);
                        if (running && !client.isConnected()) connect(options);
                    } catch (Exception e) {
                        log.warn("Không thử lại được kết nối MQTT: {}", e.getMessage());
                    }
                });
            }
        });
    }

    private class Callback implements MqttCallbackExtended {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            log.info("Đã kết nối MQTT broker {} (reconnect={})", serverURI, reconnect);
            try {
                client.subscribe(new String[]{topicTelemetry, topicAck, topicStatus}, new int[]{0, 1, 1});
            } catch (MqttException e) {
                log.error("Subscribe MQTT thất bại", e);
            }
            events.publishEvent(new BrokerConnectionChanged(true, null));
        }

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("Mất kết nối MQTT broker: {}", cause == null ? "?" : cause.getMessage());
            events.publishEvent(new BrokerConnectionChanged(false, cause == null ? null : cause.getMessage()));
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            try {
                handle(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                // Không ném lỗi ra ngoài: Paho sẽ ngắt kết nối nếu callback ném exception
                log.warn("Bỏ qua gói MQTT không hợp lệ trên {}: {}", topic, e.getMessage());
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }

    void handle(String topic, String payload) {
        Instant now = Instant.now();
        if (topic.equals(topicTelemetry)) {
            JsonNode node = json.readTree(payload);
            if (node.has("plcOk") && !node.path("plcOk").asBoolean(true)) {
                events.publishEvent(new PlcLinkDownReported(node.path("gw").asString(null),
                        node.path("err").asString(null), now));
                return;
            }
            TelemetryMessage msg = json.treeToValue(node, TelemetryMessage.class);
            if (!msg.isComplete()) {
                log.warn("Telemetry thiếu dữ liệu (cần đủ d50, d100, d200 mỗi khối {} giá trị)", TelemetryMessage.BLOCK_SIZE);
                return;
            }
            events.publishEvent(new TelemetryReceived(msg, now));
        } else if (topic.equals(topicAck)) {
            JsonNode n = json.readTree(payload);
            events.publishEvent(new CommandAckReceived(n.path("reqId").asInt(), n.path("cmd").asInt(),
                    n.path("result").asInt(), n.path("msg").asString(null), now));
        } else if (topic.equals(topicStatus)) {
            events.publishEvent(new GatewayStatusReported("online".equalsIgnoreCase(payload.trim()), now));
        }
    }

    @Override
    public void publishCommand(CommandMessage command) {
        if (client == null || !client.isConnected()) {
            throw new GatewayUnavailableException("Backend chưa kết nối được MQTT broker", null);
        }
        try {
            MqttMessage msg = new MqttMessage(json.writeValueAsBytes(command));
            msg.setQos(1);
            msg.setRetained(false); // tuyệt đối không retained: lệnh cũ không được thực hiện lại
            client.publish(topicCommand, msg).waitForCompletion(5_000);
        } catch (MqttException e) {
            throw new GatewayUnavailableException("Gửi lệnh tới MQTT broker thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isBrokerConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public String mode() {
        return "mqtt";
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (client != null) {
                if (client.isConnected()) client.disconnect().waitForCompletion(3_000);
                client.close();
            }
        } catch (MqttException e) {
            log.debug("Đóng MQTT client: {}", e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
