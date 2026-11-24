package vn.farmcontrol.notify;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import vn.farmcontrol.alert.AlertResponse;
import vn.farmcontrol.alert.AlertSeverity;
import vn.farmcontrol.config.FarmProperties;

/**
 * Thông báo đẩy Firebase Cloud Messaging tới mọi điện thoại đã đăng ký (bảng push_token).
 * Bật khi FIREBASE_CREDENTIALS trỏ tới file khóa tài khoản dịch vụ Firebase.
 */
@Component
@ConditionalOnExpression("'${farm.notifications.fcm.credentials-file:}' != ''")
public class FcmPushSender implements NotificationService.PushSender {

    /** Kênh thông báo Android, phải khớp với app Flutter. */
    public static final String ANDROID_CHANNEL = "farm_alerts";

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private final FirebaseMessaging messaging;
    private final PushTokenRepository tokens;

    public FcmPushSender(FarmProperties props, PushTokenRepository tokens) throws IOException {
        this.tokens = tokens;
        String file = props.notifications().fcm().credentialsFile();
        try (FileInputStream in = new FileInputStream(file)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
            this.messaging = FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            throw new IOException("Không đọc được file Firebase FIREBASE_CREDENTIALS=" + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void sendAlert(AlertResponse alert, boolean resolved) throws FirebaseMessagingException {
        List<PushToken> all = tokens.findAll();
        if (all.isEmpty()) return;
        boolean critical = alert.severity() == AlertSeverity.CRITICAL;
        String title = NotificationService.icon(alert, resolved) + " " + (resolved ? "Đã hết: " : "") + alert.title();
        String body = resolved ? "Tình trạng đã trở lại bình thường." : (alert.message() == null ? "" : alert.message());

        // FCM gửi tối đa 500 token mỗi lần
        for (int i = 0; i < all.size(); i += 500) {
            List<PushToken> batch = all.subList(i, Math.min(i + 500, all.size()));
            MulticastMessage msg = MulticastMessage.builder()
                    .addAllTokens(batch.stream().map(PushToken::getToken).toList())
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putData("alertId", String.valueOf(alert.id()))
                    .putData("type", alert.type().name())
                    .putData("severity", alert.severity().name())
                    .putData("resolved", String.valueOf(resolved))
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(critical ? AndroidConfig.Priority.HIGH : AndroidConfig.Priority.NORMAL)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(ANDROID_CHANNEL)
                                    .setTag(alert.type().name()) // cùng loại cảnh báo thì thay thông báo cũ
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").setThreadId(alert.type().name()).build())
                            .build())
                    .build();
            BatchResponse res = messaging.sendEachForMulticast(msg);
            removeDeadTokens(batch, res);
        }
    }

    /** Xóa token của app đã gỡ / hết hạn để lần sau không gửi nữa. */
    private void removeDeadTokens(List<PushToken> batch, BatchResponse res) {
        List<SendResponse> responses = res.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (r.isSuccessful() || r.getException() == null) continue;
            MessagingErrorCode code = r.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                tokens.delete(batch.get(i));
                log.info("Đã xóa token FCM không còn hiệu lực ({})", code);
            } else {
                log.warn("FCM gửi thất bại: {}", r.getException().getMessage());
            }
        }
    }

    @Override
    public String channelName() {
        return "Firebase Cloud Messaging";
    }
}
