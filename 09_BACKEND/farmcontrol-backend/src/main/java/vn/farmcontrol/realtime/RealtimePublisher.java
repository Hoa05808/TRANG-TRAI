package vn.farmcontrol.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import vn.farmcontrol.alert.AlertResponse;
import vn.farmcontrol.command.CommandDtos.CommandResponse;
import vn.farmcontrol.state.DashboardResponse;

/**
 * Đẩy dữ liệu thời gian thực tới app qua STOMP over WebSocket.
 *   /topic/dashboard  : ảnh chụp trạng thái mỗi khi có telemetry hoặc đổi trạng thái kết nối
 *   /topic/alerts     : cảnh báo mới / cập nhật / hết
 *   /topic/commands   : tiến trình lệnh điều khiển
 */
@Component
public class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    public static final String TOPIC_DASHBOARD = "/topic/dashboard";
    public static final String TOPIC_ALERTS = "/topic/alerts";
    public static final String TOPIC_COMMANDS = "/topic/commands";

    private final SimpMessagingTemplate template;

    public RealtimePublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void dashboard(DashboardResponse dashboard) {
        send(TOPIC_DASHBOARD, dashboard);
    }

    public void alertUpdated(AlertResponse alert) {
        send(TOPIC_ALERTS, alert);
    }

    public void commandUpdated(CommandResponse command) {
        send(TOPIC_COMMANDS, command);
    }

    private void send(String topic, Object payload) {
        try {
            template.convertAndSend(topic, payload);
        } catch (Exception e) {
            // Lỗi đẩy realtime không được làm hỏng luồng xử lý chính
            log.debug("Không đẩy được {}: {}", topic, e.getMessage());
        }
    }
}
