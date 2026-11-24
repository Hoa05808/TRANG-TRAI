package vn.farmcontrol.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "farm")
public record FarmProperties(
        String farmId,
        Security security,
        Gateway gateway,
        Mqtt mqtt,
        Telemetry telemetry,
        Notify notifications) {

    public record Security(
            String jwtSecret,
            int accessTokenMinutes,
            int refreshTokenDays,
            String bootstrapOwnerUsername,
            String bootstrapOwnerPassword) {
    }

    public enum GatewayMode { MQTT, SIMULATOR, NONE }

    public record Gateway(
            GatewayMode mode,
            int commandTtlSeconds,
            int commandAckTimeoutSeconds,
            int commandConfirmWindowSeconds) {
    }

    public record Mqtt(String url, String clientId, String username, String password) {
    }

    public record Telemetry(int storeIntervalSeconds) {
    }

    public record Notify(Telegram telegram, Fcm fcm) {
        public record Telegram(String botToken, String chatId, String minSeverity) {
            public boolean enabled() {
                return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
            }
        }

        public record Fcm(String credentialsFile) {
            public boolean enabled() {
                return credentialsFile != null && !credentialsFile.isBlank();
            }
        }
    }
}
