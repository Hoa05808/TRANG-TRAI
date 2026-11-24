package vn.farmcontrol.alert;

import java.time.Instant;

public record AlertResponse(
        Long id,
        AlertType type,
        AlertSeverity severity,
        Alert.Status status,
        String title,
        String message,
        Double value,
        Instant raisedAt,
        Instant lastSeenAt,
        Instant resolvedAt,
        Instant acknowledgedAt,
        String acknowledgedBy) {

    public static AlertResponse of(Alert a) {
        return new AlertResponse(a.getId(), a.getType(), a.getSeverity(), a.getStatus(), a.getTitle(),
                a.getMessage(), a.getValue(), a.getRaisedAt(), a.getLastSeenAt(), a.getResolvedAt(),
                a.getAcknowledgedAt(), a.getAcknowledgedBy());
    }
}
