package vn.farmcontrol.history;

import jakarta.persistence.*;
import java.time.Instant;
import vn.farmcontrol.plc.Device;

/** Một lần thiết bị đổi trạng thái (theo telemetry thực tế từ PLC). */
@Entity
@Table(name = "device_event")
public class DeviceEvent {

    /** REMOTE: do lệnh từ app; PLC: do PLC tự động / nút nhấn tại tủ / HMI. */
    public enum Source { REMOTE, PLC }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Device device;

    @Column(name = "turned_on", nullable = false)
    private boolean turnedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(name = "command_id")
    private Long commandId;

    @Column(length = 50)
    private String username;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected DeviceEvent() {
    }

    public DeviceEvent(Device device, boolean turnedOn, Source source, Long commandId, String username,
                       Instant occurredAt) {
        this.device = device;
        this.turnedOn = turnedOn;
        this.source = source;
        this.commandId = commandId;
        this.username = username;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Device getDevice() { return device; }
    public boolean isTurnedOn() { return turnedOn; }
    public Source getSource() { return source; }
    public Long getCommandId() { return commandId; }
    public String getUsername() { return username; }
    public Instant getOccurredAt() { return occurredAt; }
}
