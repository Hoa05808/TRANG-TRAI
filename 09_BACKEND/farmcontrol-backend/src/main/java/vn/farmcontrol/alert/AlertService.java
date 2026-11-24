package vn.farmcontrol.alert;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.farmcontrol.common.ApiException;
import vn.farmcontrol.notify.NotificationService;
import vn.farmcontrol.realtime.RealtimePublisher;

/**
 * Mỗi loại cảnh báo chỉ có tối đa một bản ghi ACTIVE (chống gửi thông báo lặp).
 * Khi điều kiện hết, cảnh báo chuyển RESOLVED; nếu tái diễn sẽ tạo bản ghi mới.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository repo;
    private final RealtimePublisher realtime;
    private final NotificationService notifications;

    public AlertService(AlertRepository repo, RealtimePublisher realtime, NotificationService notifications) {
        this.repo = repo;
        this.realtime = realtime;
        this.notifications = notifications;
    }

    public synchronized void raise(AlertType type, String message, Double value) {
        raise(type, type.defaultSeverity(), message, value);
    }

    public synchronized void raise(AlertType type, AlertSeverity severity, String message, Double value) {
        Instant now = Instant.now();
        Optional<Alert> active = repo.findFirstByTypeAndStatus(type, Alert.Status.ACTIVE);
        if (active.isPresent()) {
            Alert a = active.get();
            // Cảnh báo đang tồn tại: chỉ cập nhật giá trị, tối đa 15 giây/lần để giảm ghi DB
            if (a.getLastSeenAt().isBefore(now.minusSeconds(15))) {
                boolean changed = !java.util.Objects.equals(a.getMessage(), message);
                a.refresh(message, value, now);
                repo.save(a);
                if (changed) realtime.alertUpdated(AlertResponse.of(a));
            }
            return;
        }
        Alert a = repo.save(new Alert(type, severity, message, value, now));
        log.warn("CẢNH BÁO [{}] {}: {}", severity, type.title(), message);
        AlertResponse resp = AlertResponse.of(a);
        realtime.alertUpdated(resp);
        notifications.notifyAlert(resp);
    }

    public synchronized void resolve(AlertType type) {
        repo.findFirstByTypeAndStatus(type, Alert.Status.ACTIVE).ifPresent(a -> {
            a.resolve(Instant.now());
            repo.save(a);
            log.info("Hết cảnh báo {}", type.title());
            AlertResponse resp = AlertResponse.of(a);
            realtime.alertUpdated(resp);
            notifications.notifyResolved(resp);
        });
    }

    public boolean isActive(AlertType type) {
        return repo.findFirstByTypeAndStatus(type, Alert.Status.ACTIVE).isPresent();
    }

    public synchronized AlertResponse acknowledge(Long id, String username) {
        Alert a = repo.findById(id).orElseThrow(() -> ApiException.notFound("cảnh báo"));
        if (a.getAcknowledgedAt() == null) {
            a.acknowledge(username, Instant.now());
            repo.save(a);
            realtime.alertUpdated(AlertResponse.of(a));
        }
        return AlertResponse.of(a);
    }

    public List<AlertResponse> active() {
        return repo.findByStatusOrderByRaisedAtDesc(Alert.Status.ACTIVE).stream().map(AlertResponse::of).toList();
    }

    public long activeCount() {
        return repo.countByStatus(Alert.Status.ACTIVE);
    }

    public long unacknowledgedActiveCount() {
        return repo.countByStatusAndAcknowledgedAtIsNull(Alert.Status.ACTIVE);
    }
}
