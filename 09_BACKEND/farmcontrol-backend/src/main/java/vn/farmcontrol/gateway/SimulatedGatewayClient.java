package vn.farmcontrol.gateway;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import vn.farmcontrol.gateway.GatewayEvents.*;
import vn.farmcontrol.plc.CommandType;
import vn.farmcontrol.plc.Device;
import vn.farmcontrol.plc.TelemetryMessage;

/**
 * Giả lập ESP32 + PLC FX3U ngay trong backend để phát triển và thử app Flutter khi chưa có phần cứng.
 * Mô phỏng logic AUTO cơ bản: nhiệt độ cao -> bật quạt, mực nước thấp -> bật bơm.
 * KHÔNG dùng khi chạy thật (farm.gateway.mode=mqtt).
 */
@Component
@ConditionalOnProperty(name = "farm.gateway.mode", havingValue = "simulator")
public class SimulatedGatewayClient implements GatewayClient, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SimulatedGatewayClient.class);
    private static final long REMOTE_OVERRIDE_MS = 120_000;

    /** Sự cố giả lập để thử màn hình cảnh báo. */
    public enum Fault { NONE, GATEWAY_OFFLINE, PLC_LINK_LOST, PLC_ERROR, SENSOR_FAULT, WATER_LOW, TEMP_HIGH }

    private final ApplicationEventPublisher events;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "plc-simulator");
        t.setDaemon(true);
        return t;
    });

    // Trạng thái mô phỏng (chỉ truy cập trên luồng executor)
    private final EnumMap<Device, Boolean> outputs = new EnumMap<>(Device.class);
    private final EnumMap<Device, Long> remoteOverrideUntil = new EnumMap<>(Device.class);
    private double temp = 29.0;
    private double humidity = 72.0;
    private double waterLevel = 55.0;
    private int heartbeat;
    private int gatewayHeartbeat;
    private long seq;
    private int uptime;
    private int lastCmd;
    private int lastAckReq;
    private int lastResult;
    private int fishFeeds, duckFeeds, chickenFeeds;
    private volatile Fault fault = Fault.NONE;
    private volatile boolean running;

    public SimulatedGatewayClient(ApplicationEventPublisher events) {
        this.events = events;
        for (Device d : Device.values()) outputs.put(d, false);
        outputs.put(Device.LIGHT_1, true);
    }

    @Override
    public void start() {
        running = true;
        log.warn("Đang chạy BỘ GIẢ LẬP Gateway/PLC (farm.gateway.mode=simulator). Dữ liệu KHÔNG phải từ trang trại thật.");
        executor.execute(() -> events.publishEvent(new GatewayStatusReported(true, Instant.now())));
        executor.scheduleAtFixedRate(this::tickSafely, 1, 2, TimeUnit.SECONDS);
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Exception e) {
            log.error("Lỗi bộ giả lập", e);
        }
    }

    private void tick() {
        uptime += 2;
        gatewayHeartbeat = (gatewayHeartbeat + 1) & 0x7FFF;
        if (fault != Fault.PLC_LINK_LOST) heartbeat = (heartbeat + 1) & 0x7FFF;

        // Môi trường: nhiệt độ dao động theo chu kỳ ~10 phút, quạt làm mát
        double phase = (System.currentTimeMillis() % 600_000) / 600_000.0 * 2 * Math.PI;
        double ambient = 31.0 + 4.5 * Math.sin(phase);
        int fans = (outputs.get(Device.FAN_1) ? 1 : 0) + (outputs.get(Device.FAN_2) ? 1 : 0);
        temp += (ambient - temp) * 0.08 - fans * 0.12 + noise(0.05);
        humidity += (75 - humidity) * 0.05 - fans * 0.1 + noise(0.3);
        waterLevel += (outputs.get(Device.PUMP_1) ? 0.9 : 0) - 0.25 - (outputs.get(Device.VALVE_1) ? 0.3 : 0);
        waterLevel = clamp(waterLevel, 0, 100);
        humidity = clamp(humidity, 20, 99);

        double reportedTemp = fault == Fault.TEMP_HIGH ? 38.5 : temp;
        double reportedLevel = fault == Fault.WATER_LOW ? 12.0 : waterLevel;

        // Logic AUTO của PLC (có hiện trễ), bỏ qua thiết bị vừa được điều khiển từ xa
        long now = System.currentTimeMillis();
        autoSet(Device.FAN_1, now, reportedTemp >= 30.0, reportedTemp <= 28.0);
        autoSet(Device.FAN_2, now, reportedTemp >= 33.0, reportedTemp <= 31.0);
        autoSet(Device.PUMP_1, now, reportedLevel <= 30.0, reportedLevel >= 80.0);

        if (fault == Fault.GATEWAY_OFFLINE) {
            return; // không gửi gì, giống mất mạng
        }
        if (fault == Fault.PLC_LINK_LOST) {
            // Giống ESP32 thật: không đọc được PLC thì báo plcOk=false, không gửi số liệu cũ
            events.publishEvent(new PlcLinkDownReported("simulator", "Modbus timeout (giả lập)", Instant.now()));
            return;
        }

        int[] d50 = {
                (int) Math.round(reportedTemp * 10), (int) Math.round(humidity * 10), 285 + (int) noise(3),
                (int) Math.round(reportedLevel * 10), 550, 1200 + (int) noise(40), 800 + (int) noise(20),
                10, fault == Fault.SENSOR_FAULT ? 0 : 1, fault == Fault.SENSOR_FAULT ? 0 : 1};
        int[] d100 = new int[10];
        int[] d110 = new int[10];
        for (Device d : Device.values()) {
            int i = d.statusIndex();
            if (i < 10) d100[i] = outputs.get(d) ? 1 : 0;
            else d110[i - 10] = outputs.get(d) ? 1 : 0;
        }
        java.time.LocalTime now0 = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        d110[4] = 0;              // thùng thức ăn: còn đủ
        d110[5] = fishFeeds;      // số lần cho ăn hôm nay
        d110[6] = duckFeeds;
        d110[7] = chickenFeeds;
        d110[8] = 1;              // cho phép cho ăn
        d110[9] = now0.getHour() * 100 + now0.getMinute();
        d100[5] = 1; // AUTO
        d100[6] = fault == Fault.PLC_ERROR ? 2 : 1;
        d100[7] = fault == Fault.PLC_ERROR ? 133 : 0;
        d100[8] = 0;
        d100[9] = uptime / 60; // D109 tính bằng phút, giống PLC
        int[] d200 = {1, heartbeat, gatewayHeartbeat, lastCmd, 0, lastAckReq, lastResult,
                fault == Fault.PLC_LINK_LOST ? 1 : 0, (int) (seq & 0x7FFF), 1};

        events.publishEvent(new TelemetryReceived(
                new TelemetryMessage("simulator", ++seq, d50, d100, d110, d200), Instant.now()));
    }

    private void autoSet(Device d, long now, boolean onCondition, boolean offCondition) {
        Long until = remoteOverrideUntil.get(d);
        if (until != null && until > now) return;
        if (onCondition) outputs.put(d, true);
        else if (offCondition) outputs.put(d, false);
    }

    @Override
    public void publishCommand(CommandMessage cmd) {
        if (!running) throw new GatewayUnavailableException("Bộ giả lập chưa chạy", null);
        if (fault == Fault.GATEWAY_OFFLINE) {
            // Giống thực tế: broker nhận lệnh nhưng Gateway không có mặt -> không có phản hồi -> TIMEOUT
            return;
        }
        executor.schedule(() -> {
            if (Instant.now().getEpochSecond() > cmd.exp()) {
                ack(cmd, 10, "Lệnh đã hết hạn khi tới Gateway");
                return;
            }
            CommandType type = CommandType.fromPlcCode(cmd.cmd()).orElse(null);
            if (type == null) {
                ack(cmd, 2, "Mã lệnh không hợp lệ");
                return;
            }
            if (fault == Fault.PLC_LINK_LOST) {
                ack(cmd, 11, "Không giao tiếp được với PLC qua RS485");
                return;
            }
            ack(cmd, 1, null);
            executor.schedule(() -> {
                long until = System.currentTimeMillis() + REMOTE_OVERRIDE_MS;
                for (Map.Entry<Device, Boolean> e : type.expected().entrySet()) {
                    outputs.put(e.getKey(), e.getValue());
                    remoteOverrideUntil.put(e.getKey(), until);
                }
                simulateFeedCycle(type);
                ack(cmd, 3, null);
            }, 600, TimeUnit.MILLISECONDS);
        }, 300, TimeUnit.MILLISECONDS);
    }

    /** Giống PLC: cho ăn chạy theo thời gian rồi tự tắt (rút ngắn còn ~20 s để thử app). */
    private void simulateFeedCycle(CommandType type) {
        switch (type) {
            case FEED_FISH_START -> {
                fishFeeds++;
                executor.schedule(() -> outputs.put(Device.FISH_VALVE, true), 3, TimeUnit.SECONDS);
                executor.schedule(() -> outputs.put(Device.FISH_VALVE, false), 15, TimeUnit.SECONDS);
                executor.schedule(() -> outputs.put(Device.FISH_FEEDER, false), 20, TimeUnit.SECONDS);
            }
            case FEED_DUCK_START -> {
                duckFeeds++;
                executor.schedule(() -> outputs.put(Device.DUCK_VALVE, false), 15, TimeUnit.SECONDS);
            }
            case VALVE_OPEN -> executor.schedule(() -> outputs.put(Device.VALVE_1, false), 15, TimeUnit.SECONDS);
            case FEED_CHICKEN_START -> {
                chickenFeeds++;
                executor.schedule(() -> outputs.put(Device.CHICKEN_VALVE, false), 15, TimeUnit.SECONDS);
            }
            default -> { }
        }
    }

    private void ack(CommandMessage cmd, int result, String msg) {
        lastCmd = cmd.cmd();
        lastAckReq = cmd.reqId();
        lastResult = result;
        events.publishEvent(new CommandAckReceived(cmd.reqId(), cmd.cmd(), result, msg, Instant.now()));
    }

    public void setFault(Fault fault) {
        log.info("Bộ giả lập: đặt sự cố {}", fault);
        Fault previous = this.fault;
        this.fault = fault;
        if (fault == Fault.GATEWAY_OFFLINE) {
            executor.execute(() -> events.publishEvent(new GatewayStatusReported(false, Instant.now())));
        } else if (previous == Fault.GATEWAY_OFFLINE) {
            executor.execute(() -> events.publishEvent(new GatewayStatusReported(true, Instant.now())));
        }
    }

    public Fault getFault() {
        return fault;
    }

    private static double noise(double amplitude) {
        return ThreadLocalRandom.current().nextDouble(-amplitude, amplitude);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public boolean isBrokerConnected() {
        return running;
    }

    @Override
    public String mode() {
        return "simulator";
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
