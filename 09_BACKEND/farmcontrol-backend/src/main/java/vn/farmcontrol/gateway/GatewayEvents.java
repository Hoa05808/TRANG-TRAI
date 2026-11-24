package vn.farmcontrol.gateway;

import java.time.Instant;
import vn.farmcontrol.plc.TelemetryMessage;

/** Sự kiện đi vào từ Gateway (MQTT hoặc bộ giả lập). Các service lắng nghe bằng @EventListener. */
public final class GatewayEvents {

    private GatewayEvents() {
    }

    public record TelemetryReceived(TelemetryMessage message, Instant receivedAt) {
    }

    /**
     * Phản hồi lệnh từ ESP32 (topic farm/{farmId}/command/ack).
     * result theo D206: 1 = chấp nhận, 2 = từ chối, 3 = đã thực hiện, 4 = lỗi thực hiện;
     * mã do Gateway tự sinh: 10 = lệnh đã hết hạn khi tới Gateway, 11 = lỗi truyền thông RS485.
     */
    public record CommandAckReceived(int reqId, int cmd, int result, String msg, Instant receivedAt) {
    }

    /** Trạng thái Gateway báo qua topic farm/{farmId}/gateway/status (retained + Last Will). */
    public record GatewayStatusReported(boolean online, Instant receivedAt) {
    }

    /** ESP32 báo không đọc được PLC qua RS485: {"gw":"esp32-01","plcOk":false,"err":"timeout"}. */
    public record PlcLinkDownReported(String gatewayId, String error, Instant receivedAt) {
    }

    /** Kết nối giữa backend và MQTT broker thay đổi. */
    public record BrokerConnectionChanged(boolean connected, String reason) {
    }
}
