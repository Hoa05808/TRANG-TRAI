package vn.farmcontrol.notify;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.farmcontrol.alert.AlertResponse;
import vn.farmcontrol.alert.AlertSeverity;

/**
 * Gửi thông báo khi cảnh báo xuất hiện / kết thúc qua các kênh đã cấu hình:
 *   - FcmPushSender      : thông báo đẩy vào app (kênh chính)
 *   - TelegramPushSender : nhóm Telegram (kênh dự phòng, mặc định chỉ CRITICAL)
 * Gửi trên luồng riêng để mạng chậm không làm trễ xử lý telemetry.
 */
@Service
public class NotificationService {

    /** Một kênh gửi thông báo. */
    public interface PushSender {
        /** @param resolved true khi cảnh báo đã kết thúc */
        void sendAlert(AlertResponse alert, boolean resolved) throws Exception;

        String channelName();
    }

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final List<PushSender> senders;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public NotificationService(List<PushSender> senders) {
        this.senders = senders;
        if (senders.isEmpty()) {
            log.info("Chưa cấu hình kênh thông báo (FIREBASE_CREDENTIALS / TELEGRAM_BOT_TOKEN): cảnh báo chỉ ghi log");
        } else {
            log.info("Kênh thông báo: {}", senders.stream().map(PushSender::channelName).toList());
        }
    }

    public void notifyAlert(AlertResponse alert) {
        dispatch(alert, false);
    }

    public void notifyResolved(AlertResponse alert) {
        dispatch(alert, true);
    }

    private void dispatch(AlertResponse alert, boolean resolved) {
        if (alert.severity() == AlertSeverity.INFO) return;
        if (senders.isEmpty()) {
            log.info("[THÔNG BÁO] {}{} - {}", resolved ? "Đã hết: " : "", alert.title(), alert.message());
            return;
        }
        for (PushSender sender : senders) {
            executor.execute(() -> {
                try {
                    sender.sendAlert(alert, resolved);
                } catch (Exception e) {
                    log.warn("Gửi thông báo qua {} thất bại: {}", sender.channelName(), e.getMessage());
                }
            });
        }
    }

    static String icon(AlertResponse alert, boolean resolved) {
        if (resolved) return "✅";
        return alert.severity() == AlertSeverity.CRITICAL ? "🚨" : "⚠️";
    }
}
