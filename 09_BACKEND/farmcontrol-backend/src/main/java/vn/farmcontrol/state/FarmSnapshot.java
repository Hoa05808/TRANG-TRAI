package vn.farmcontrol.state;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import vn.farmcontrol.plc.Device;
import vn.farmcontrol.plc.PlcEnums.OperationMode;
import vn.farmcontrol.plc.PlcEnums.PlcState;
import vn.farmcontrol.plc.TelemetryMessage;

/** Ảnh chụp trạng thái trang trại từ gói telemetry gần nhất (đã quy đổi đơn vị). */
public record FarmSnapshot(
        String gatewayId,
        long seq,
        Instant receivedAt,
        SensorValues sensors,
        Map<Device, Boolean> devices,
        OperationMode mode,
        PlcState plcState,
        int errorCode,
        int alarmCode,
        int uptime,
        boolean remoteEnabled,
        int plcHeartbeat,
        int commErrorCode,
        FeedStatus feed) {

    /** Cho ăn (D110..D119). null nếu Gateway chưa gửi khối d110 (firmware cũ). */
    public record FeedStatus(
            boolean fishLow,
            boolean duckLow,
            boolean chickenLow,
            int fishToday,
            int duckToday,
            int chickenToday,
            boolean enabled,
            String plcClock) {

        public boolean anyLow() { return fishLow || duckLow || chickenLow; }

        static FeedStatus from(TelemetryMessage m) {
            if (!m.hasFeed()) return null;
            int mask = m.feedLowMask();
            int hhmm = m.plcClockHhmm();
            String clock = hhmm >= 0 && hhmm <= 2359 && hhmm % 100 < 60
                    ? "%02d:%02d".formatted(hhmm / 100, hhmm % 100) : null;
            return new FeedStatus((mask & TelemetryMessage.FEED_LOW_FISH) != 0,
                    (mask & TelemetryMessage.FEED_LOW_DUCK) != 0, (mask & TelemetryMessage.FEED_LOW_CHICKEN) != 0,
                    m.fishFeedsToday(), m.duckFeedsToday(), m.chickenFeedsToday(), m.feedEnabled(), clock);
        }
    }

    public record SensorValues(
            double coopTemp,
            double airHumidity,
            double waterTemp,
            double waterLevel,
            double soilHumidity,
            int lightLux,
            int co2Ppm,
            int nh3Ppm,
            boolean tempSensorOk,
            boolean dataValid) {
    }

    public static FarmSnapshot from(TelemetryMessage m, Instant receivedAt) {
        SensorValues s = new SensorValues(
                m.coopTempRaw() / 10.0, m.airHumidityRaw() / 10.0, m.waterTempRaw() / 10.0,
                m.waterLevelRaw() / 10.0, m.soilHumidityRaw() / 10.0,
                m.lightLux(), m.co2Ppm(), m.nh3Ppm(), m.tempSensorOk(), m.sensorDataValid());
        EnumMap<Device, Boolean> devices = new EnumMap<>(Device.class);
        for (Device d : Device.values()) devices.put(d, m.deviceOn(d));
        return new FarmSnapshot(m.gw(), m.seq() == null ? 0 : m.seq(), receivedAt, s,
                Collections.unmodifiableMap(devices),
                OperationMode.fromRegister(m.operationMode()), PlcState.fromRegister(m.plcState()),
                m.errorCode(), m.alarmCode(), m.uptime(), m.remoteEnabled(), m.plcHeartbeat(), m.commErrorCode(),
                FeedStatus.from(m));
    }

    public boolean isOn(Device d) {
        return Boolean.TRUE.equals(devices.get(d));
    }
}
