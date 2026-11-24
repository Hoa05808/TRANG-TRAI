package vn.farmcontrol.gateway;

/** Kênh gửi lệnh xuống Gateway ESP32. */
public interface GatewayClient {

    /**
     * Gói lệnh gửi lên topic farm/{farmId}/command:
     * {"reqId":1234,"cmd":1,"val":0,"exp":1732450000,"ts":1732449990}
     * exp/ts là epoch giây. ESP32 PHẢI bỏ lệnh nếu thời gian hiện tại > exp.
     */
    record CommandMessage(int reqId, int cmd, int val, long exp, long ts) {
    }

    /** @throws GatewayUnavailableException nếu không gửi được tới broker */
    void publishCommand(CommandMessage command);

    boolean isBrokerConnected();

    String mode();

    class GatewayUnavailableException extends RuntimeException {
        public GatewayUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
