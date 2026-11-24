package vn.farmcontrol.command;

import java.util.EnumSet;
import java.util.Set;

/**
 * Vòng đời lệnh:
 * <pre>
 *  (kiểm tra trước) --x--> BLOCKED            (mất kết nối / PLC không cho phép điều khiển từ xa)
 *  PENDING -> SENT -> ACCEPTED -> EXECUTED    (PLC đã thực hiện; confirmedAt khi telemetry khớp)
 *                 \-> REJECTED                (PLC từ chối, ví dụ do liên động an toàn)
 *                 \-> FAILED                  (lỗi gửi MQTT / lỗi thực hiện / lỗi RS485)
 *                 \-> EXPIRED                 (Gateway nhận lệnh khi đã hết hạn)
 *                 \-> TIMEOUT                 (không phản hồi - KHÔNG tự gửi lại)
 * </pre>
 */
public enum CommandStatus {
    PENDING, SENT, ACCEPTED, EXECUTED, REJECTED, FAILED, EXPIRED, TIMEOUT, BLOCKED;

    public static final Set<CommandStatus> IN_FLIGHT = EnumSet.of(PENDING, SENT, ACCEPTED);

    public boolean isFinal() {
        return !IN_FLIGHT.contains(this);
    }
}
