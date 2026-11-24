package vn.farmcontrol.state;

import java.time.Instant;
import java.util.List;
import vn.farmcontrol.plc.Device;
import vn.farmcontrol.plc.PlcEnums.OperationMode;
import vn.farmcontrol.plc.PlcEnums.PlcState;
import vn.farmcontrol.state.FarmSnapshot.SensorValues;
import vn.farmcontrol.state.FarmStateService.ConnectionStatus;

/**
 * Dữ liệu màn hình Dashboard / Điều khiển.
 * Khi connection != ONLINE: devices[].state = UNKNOWN, mode/plcState = UNKNOWN,
 * sensors giữ giá trị cuối cùng (app hiển thị mờ kèm lastUpdate).
 */
public record DashboardResponse(
        ConnectionStatus connection,
        boolean dataFresh,
        Instant lastUpdate,
        String gatewayMode,
        SensorValues sensors,
        List<DeviceView> devices,
        OperationMode mode,
        PlcState plcState,
        int errorCode,
        int alarmCode,
        boolean remoteEnabled,
        int plcUptime,
        long activeAlerts,
        long unacknowledgedAlerts,
        Instant serverTime) {

    public enum DeviceState { ON, OFF, UNKNOWN }

    public record DeviceView(Device id, String name, String plcOutput, DeviceState state, Instant onSince) {
    }
}
