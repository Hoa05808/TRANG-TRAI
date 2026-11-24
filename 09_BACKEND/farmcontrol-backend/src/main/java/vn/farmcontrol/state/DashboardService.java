package vn.farmcontrol.state;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.farmcontrol.alert.AlertService;
import vn.farmcontrol.gateway.GatewayClient;
import vn.farmcontrol.plc.Device;
import vn.farmcontrol.plc.PlcEnums.OperationMode;
import vn.farmcontrol.plc.PlcEnums.PlcState;
import vn.farmcontrol.state.DashboardResponse.DeviceState;
import vn.farmcontrol.state.DashboardResponse.DeviceView;
import vn.farmcontrol.state.FarmStateService.ConnectionStatus;

@Service
public class DashboardService {

    private final FarmStateService state;
    private final AlertService alerts;
    private final GatewayClient gateway;

    public DashboardService(FarmStateService state, AlertService alerts, GatewayClient gateway) {
        this.state = state;
        this.alerts = alerts;
        this.gateway = gateway;
    }

    public DashboardResponse build() {
        FarmSnapshot snap = state.latest();
        ConnectionStatus conn = state.connectionStatus();
        boolean online = conn == ConnectionStatus.ONLINE;
        List<DeviceView> devices = Arrays.stream(Device.values())
                .map(d -> new DeviceView(d, d.displayName(), d.plcOutput(), deviceState(snap, d, online),
                        online && snap.isOn(d) ? state.onSince(d) : null))
                .toList();
        return new DashboardResponse(
                conn,
                state.isDataFresh(),
                snap == null ? null : snap.receivedAt(),
                gateway.mode(),
                snap == null ? null : snap.sensors(),
                devices,
                online ? snap.mode() : OperationMode.UNKNOWN,
                online ? snap.plcState() : PlcState.UNKNOWN,
                snap == null ? 0 : snap.errorCode(),
                snap == null ? 0 : snap.alarmCode(),
                online && snap.remoteEnabled(),
                snap == null ? 0 : snap.uptime(),
                alerts.activeCount(),
                alerts.unacknowledgedActiveCount(),
                Instant.now());
    }

    private static DeviceState deviceState(FarmSnapshot snap, Device d, boolean online) {
        if (!online || snap == null) return DeviceState.UNKNOWN;
        return snap.isOn(d) ? DeviceState.ON : DeviceState.OFF;
    }
}
