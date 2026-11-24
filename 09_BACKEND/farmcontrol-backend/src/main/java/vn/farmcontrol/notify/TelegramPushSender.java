package vn.farmcontrol.notify;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.farmcontrol.alert.AlertResponse;
import vn.farmcontrol.alert.AlertSeverity;
import vn.farmcontrol.config.FarmProperties;

/** Gửi cảnh báo vào nhóm Telegram qua Bot API. Bật khi có TELEGRAM_BOT_TOKEN và TELEGRAM_CHAT_ID. */
@Component
@ConditionalOnExpression("'${farm.notifications.telegram.bot-token:}' != '' and '${farm.notifications.telegram.chat-id:}' != ''")
public class TelegramPushSender implements NotificationService.PushSender {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final RestClient http;
    private final String chatId;
    private final AlertSeverity minSeverity;

    @org.springframework.beans.factory.annotation.Autowired
    public TelegramPushSender(FarmProperties props) {
        this(props.notifications().telegram(), "https://api.telegram.org");
    }

    /** apiBase tách riêng để test bằng máy chủ giả. */
    TelegramPushSender(FarmProperties.Notify.Telegram cfg, String apiBase) {
        this.http = RestClient.builder().baseUrl(apiBase + "/bot" + cfg.botToken()).build();
        this.chatId = cfg.chatId();
        this.minSeverity = parse(cfg.minSeverity());
    }

    private static AlertSeverity parse(String s) {
        try {
            return AlertSeverity.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return AlertSeverity.CRITICAL;
        }
    }

    @Override
    public void sendAlert(AlertResponse alert, boolean resolved) {
        if (alert.severity().ordinal() < minSeverity.ordinal()) return;
        String text = resolved
                ? "%s <b>ĐÃ HẾT: %s</b>\n%s".formatted(NotificationService.icon(alert, true), esc(alert.title()),
                        TIME.format(alert.resolvedAt() != null ? alert.resolvedAt() : java.time.Instant.now()))
                : "%s <b>CẢNH BÁO TRANG TRẠI</b>\n<b>%s</b>\n%s\n<i>%s</i>".formatted(
                        NotificationService.icon(alert, false), esc(alert.title()),
                        esc(alert.message() == null ? "" : alert.message()), TIME.format(alert.raisedAt()));
        http.post().uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text, "parse_mode", "HTML"))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public String channelName() {
        return "Telegram (từ mức " + minSeverity + ")";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
