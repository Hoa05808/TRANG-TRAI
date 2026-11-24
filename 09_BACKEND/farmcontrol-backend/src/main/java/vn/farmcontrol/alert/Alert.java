package vn.farmcontrol.alert;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alert")
public class Alert {

    public enum Status { ACTIVE, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 40)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String message;

    @Column(name = "alert_value")
    private Double value;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 50)
    private String acknowledgedBy;

    protected Alert() {
    }

    public Alert(AlertType type, AlertSeverity severity, String message, Double value, Instant at) {
        this.type = type;
        this.severity = severity;
        this.status = Status.ACTIVE;
        this.title = type.title();
        this.message = truncate(message);
        this.value = value;
        this.raisedAt = at;
        this.lastSeenAt = at;
    }

    public void refresh(String message, Double value, Instant at) {
        this.message = truncate(message);
        this.value = value;
        this.lastSeenAt = at;
    }

    public void resolve(Instant at) {
        this.status = Status.RESOLVED;
        this.resolvedAt = at;
    }

    public void acknowledge(String username, Instant at) {
        this.acknowledgedBy = username;
        this.acknowledgedAt = at;
    }

    private static String truncate(String s) {
        return s != null && s.length() > 500 ? s.substring(0, 500) : s;
    }

    public Long getId() { return id; }
    public AlertType getType() { return type; }
    public AlertSeverity getSeverity() { return severity; }
    public Status getStatus() { return status; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Double getValue() { return value; }
    public Instant getRaisedAt() { return raisedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
}
