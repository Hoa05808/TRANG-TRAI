package vn.farmcontrol.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Không kết nối Gateway (dùng khi chạy test). Mọi lệnh gửi đi đều thất bại. */
@Component
@ConditionalOnProperty(name = "farm.gateway.mode", havingValue = "none")
public class NoopGatewayClient implements GatewayClient {

    @Override
    public void publishCommand(CommandMessage command) {
        throw new GatewayUnavailableException("Chưa cấu hình Gateway (farm.gateway.mode=none)", null);
    }

    @Override
    public boolean isBrokerConnected() {
        return false;
    }

    @Override
    public String mode() {
        return "none";
    }
}
