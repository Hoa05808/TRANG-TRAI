package vn.farmcontrol.command;

import jakarta.persistence.*;
import java.time.Instant;
import vn.farmcontrol.plc.CommandType;

@Entity
@Table(name = "device_command")
public class DeviceCommand {

    /** D203/D205 là thanh ghi 16 bit có dấu -> mã yêu cầu nằm trong 1..32767. */
    public static final int MAX_REQUEST_ID = 32767;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 30)
    private CommandType type;

    @Column(name = "plc_code", nullable = false)
    private int plcCode;

    @Column(name = "command_value", nullable = false)
    private int value;

    @Column(name = "request_id", nullable = false)
    private int requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommandStatus status;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(length = 255)
    private String message;

    @Column(name = "requested_by_id")
    private Long requestedById;

    @Column(name = "requested_by_name", nullable = false, length = 50)
    private String requestedByName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected DeviceCommand() {
    }

    public DeviceCommand(CommandType type, Long requestedById, String requestedByName, Instant expiresAt) {
        this.type = type;
        this.plcCode = type.plcCode();
        this.value = 0;
        this.requestedById = requestedById;
        this.requestedByName = requestedByName;
        this.status = CommandStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public void assignRequestId() {
        this.requestId = (int) (id % MAX_REQUEST_ID) + 1;
    }

    public void markSent() {
        status = CommandStatus.SENT;
        sentAt = Instant.now();
    }

    public void markAccepted(Instant at) {
        status = CommandStatus.ACCEPTED;
        ackedAt = at;
    }

    public void finish(CommandStatus finalStatus, Integer resultCode, String message, Instant at) {
        this.status = finalStatus;
        this.resultCode = resultCode;
        if (message != null) this.message = truncate(message);
        if (ackedAt == null && resultCode != null) ackedAt = at;
        this.completedAt = at;
    }

    public void confirm(Instant at) {
        this.confirmedAt = at;
    }

    private static String truncate(String s) {
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    public Long getId() { return id; }
    public CommandType getType() { return type; }
    public int getPlcCode() { return plcCode; }
    public int getValue() { return value; }
    public int getRequestId() { return requestId; }
    public CommandStatus getStatus() { return status; }
    public Integer getResultCode() { return resultCode; }
    public String getMessage() { return message; }
    public Long getRequestedById() { return requestedById; }
    public String getRequestedByName() { return requestedByName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getAckedAt() { return ackedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
