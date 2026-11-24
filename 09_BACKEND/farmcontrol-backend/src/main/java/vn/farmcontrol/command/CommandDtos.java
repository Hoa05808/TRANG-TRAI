package vn.farmcontrol.command;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import vn.farmcontrol.plc.CommandType;

public final class CommandDtos {

    private CommandDtos() {
    }

    public record CreateCommandRequest(@NotNull CommandType type) {
    }

    public record CommandResponse(
            Long id,
            CommandType type,
            String typeName,
            CommandStatus status,
            Integer resultCode,
            String message,
            String requestedBy,
            Instant createdAt,
            Instant sentAt,
            Instant ackedAt,
            Instant completedAt,
            Instant confirmedAt,
            Instant expiresAt) {

        public static CommandResponse of(DeviceCommand c) {
            return new CommandResponse(c.getId(), c.getType(), c.getType().displayName(), c.getStatus(),
                    c.getResultCode(), c.getMessage(), c.getRequestedByName(), c.getCreatedAt(), c.getSentAt(),
                    c.getAckedAt(), c.getCompletedAt(), c.getConfirmedAt(), c.getExpiresAt());
        }
    }
}
