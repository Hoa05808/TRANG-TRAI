package vn.farmcontrol.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.farmcontrol.alert.Alert;
import vn.farmcontrol.alert.AlertResponse;
import vn.farmcontrol.alert.AlertSeverity;
import vn.farmcontrol.alert.AlertType;
import vn.farmcontrol.config.FarmProperties;

/** Kiểm tra định dạng tin nhắn và bộ lọc mức độ, dùng máy chủ HTTP giả thay cho Telegram. */
class TelegramPushSenderTest {

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private TelegramPushSender sender;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            requests.add(ex.getRequestURI().getPath() + " " + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, ok.length);
            ex.getResponseBody().write(ok);
            ex.close();
        });
        server.start();
        sender = new TelegramPushSender(new FarmProperties.Notify.Telegram("TEST-TOKEN", "-100123", "CRITICAL"),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static AlertResponse alert(AlertType type, AlertSeverity severity, String message) {
        return new AlertResponse(1L, type, severity, Alert.Status.ACTIVE, type.title(), message, null,
                Instant.parse("2026-11-24T07:30:00Z"), Instant.now(), null, null, null);
    }

    @Test
    void sendsCriticalAlertWithEscapedHtml() {
        sender.sendAlert(alert(AlertType.TEMP_HIGH, AlertSeverity.CRITICAL, "Nhiệt độ 38.5°C > ngưỡng <35>"), false);
        assertThat(requests).hasSize(1);
        String req = requests.getFirst();
        assertThat(req).startsWith("/botTEST-TOKEN/sendMessage ");
        assertThat(req).contains("\"chat_id\":\"-100123\"").contains("\"parse_mode\":\"HTML\"");
        assertThat(req).contains("Nhiệt độ chuồng cao").contains("&lt;35&gt;").contains("14:30:00 24/11/2026");
    }

    @Test
    void skipsBelowMinimumSeverity() {
        sender.sendAlert(alert(AlertType.WATER_LOW, AlertSeverity.WARNING, "Mực nước 15%"), false);
        assertThat(requests).isEmpty();
    }

    @Test
    void sendsResolvedMessage() {
        sender.sendAlert(alert(AlertType.GATEWAY_OFFLINE, AlertSeverity.CRITICAL, "x"), true);
        assertThat(requests.getFirst()).contains("ĐÃ HẾT: Mất kết nối Gateway");
    }
}
