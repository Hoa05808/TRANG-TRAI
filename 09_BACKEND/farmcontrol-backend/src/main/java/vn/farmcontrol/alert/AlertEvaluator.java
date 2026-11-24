package vn.farmcontrol.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.farmcontrol.plc.Device;
import vn.farmcontrol.plc.PlcEnums.PlcState;
import vn.farmcontrol.setting.SettingKey;
import vn.farmcontrol.setting.SettingsService;
import vn.farmcontrol.state.FarmSnapshot;
import vn.farmcontrol.state.FarmSnapshot.SensorValues;
import vn.farmcontrol.state.FarmStateService;
import vn.farmcontrol.state.FarmStateService.ConnectionStatus;

/**
 * Đánh giá điều kiện cảnh báo.
 *  - evaluate(snapshot): chạy mỗi khi có telemetry.
 *  - checkConnectivity(): chạy định kỳ, vì mất kết nối nghĩa là KHÔNG có telemetry để kích hoạt.
 * Ngưỡng có độ trễ (deadband) để cảnh báo không bật/tắt liên tục quanh ngưỡng.
 */
@Component
public class AlertEvaluator {

    private static final java.time.format.DateTimeFormatter LOCAL_TIME = java.time.format.DateTimeFormatter
            .ofPattern("HH:mm:ss dd/MM/yyyy").withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));

    private final AlertService alerts;
    private final SettingsService settings;
    private final FarmStateService state;

    public AlertEvaluator(AlertService alerts, SettingsService settings, FarmStateService state) {
        this.alerts = alerts;
        this.settings = settings;
        this.state = state;
    }

    public void evaluate(FarmSnapshot snap) {
        SensorValues s = snap.sensors();

        // Lỗi cảm biến: không đánh giá ngưỡng trên dữ liệu không hợp lệ
        if (!s.dataValid() || !s.tempSensorOk()) {
            alerts.raise(AlertType.SENSOR_FAULT, !s.tempSensorOk()
                    ? "Cảm biến nhiệt độ báo lỗi (D58). Kiểm tra dây và nguồn cảm biến."
                    : "Dữ liệu cảm biến không hợp lệ (D59).", null);
        } else {
            alerts.resolve(AlertType.SENSOR_FAULT);
            evaluateThresholds(snap);
        }

        // Mã lỗi / cảnh báo từ PLC
        if (snap.plcState() == PlcState.ERROR || snap.errorCode() != 0) {
            alerts.raise(AlertType.PLC_ERROR, "PLC báo lỗi, mã lỗi D107 = " + snap.errorCode()
                    + (snap.plcState() == PlcState.ERROR ? " (PLC ở trạng thái ERROR)" : ""), (double) snap.errorCode());
        } else {
            alerts.resolve(AlertType.PLC_ERROR);
        }
        if (snap.alarmCode() != 0) {
            alerts.raise(AlertType.PLC_ALARM, "PLC báo cảnh báo, mã D108 = " + snap.alarmCode(), (double) snap.alarmCode());
        } else {
            alerts.resolve(AlertType.PLC_ALARM);
        }

        evaluatePump(snap);
    }

    private void evaluateThresholds(FarmSnapshot snap) {
        SensorValues s = snap.sensors();
        double tempHigh = settings.get(SettingKey.ALERT_TEMP_HIGH);
        double tempBand = settings.get(SettingKey.ALERT_TEMP_DEADBAND);
        if (s.coopTemp() >= tempHigh) {
            alerts.raise(AlertType.TEMP_HIGH, "Nhiệt độ chuồng %s°C, vượt ngưỡng %s°C. Quạt 1: %s, quạt 2: %s."
                    .formatted(f(s.coopTemp()), f(tempHigh), onOff(snap, Device.FAN_1), onOff(snap, Device.FAN_2)),
                    s.coopTemp());
        } else if (s.coopTemp() <= tempHigh - tempBand) {
            alerts.resolve(AlertType.TEMP_HIGH);
        }

        double waterLow = settings.get(SettingKey.ALERT_WATER_LOW);
        double waterBand = settings.get(SettingKey.ALERT_WATER_DEADBAND);
        if (s.waterLevel() <= waterLow) {
            alerts.raise(AlertType.WATER_LOW, "Mực nước bể %s%%, dưới ngưỡng %s%%. Bơm: %s. Kiểm tra nguồn cấp nước."
                    .formatted(f(s.waterLevel()), f(waterLow), onOff(snap, Device.PUMP_1)), s.waterLevel());
        } else if (s.waterLevel() >= waterLow + waterBand) {
            alerts.resolve(AlertType.WATER_LOW);
        }

        double humHigh = settings.get(SettingKey.ALERT_HUMIDITY_HIGH);
        double humLow = settings.get(SettingKey.ALERT_HUMIDITY_LOW);
        double humBand = 2.0;
        if (s.airHumidity() >= humHigh) {
            alerts.raise(AlertType.HUMIDITY_HIGH, "Độ ẩm không khí %s%%, vượt ngưỡng %s%%."
                    .formatted(f(s.airHumidity()), f(humHigh)), s.airHumidity());
        } else if (s.airHumidity() <= humHigh - humBand) {
            alerts.resolve(AlertType.HUMIDITY_HIGH);
        }
        if (s.airHumidity() <= humLow) {
            alerts.raise(AlertType.HUMIDITY_LOW, "Độ ẩm không khí %s%%, dưới ngưỡng %s%%."
                    .formatted(f(s.airHumidity()), f(humLow)), s.airHumidity());
        } else if (s.airHumidity() >= humLow + humBand) {
            alerts.resolve(AlertType.HUMIDITY_LOW);
        }
    }

    private void evaluatePump(FarmSnapshot snap) {
        Instant since = state.onSince(Device.PUMP_1);
        if (!snap.isOn(Device.PUMP_1) || since == null) {
            alerts.resolve(AlertType.PUMP_RUNTIME_EXCEEDED);
            alerts.resolve(AlertType.PUMP_NO_WATER_RISE);
            return;
        }
        long runSec = Duration.between(since, snap.receivedAt()).toSeconds();
        int maxRun = settings.getInt(SettingKey.ALERT_PUMP_MAX_RUN_SECONDS);
        if (runSec >= maxRun) {
            alerts.raise(AlertType.PUMP_RUNTIME_EXCEEDED, "Bơm đã chạy liên tục %d phút %d giây (tối đa %d giây)."
                    .formatted(runSec / 60, runSec % 60, maxRun), (double) runSec);
        }
        Double startLevel = state.waterLevelAtPumpStart();
        double minRise = settings.get(SettingKey.ALERT_PUMP_MIN_LEVEL_RISE);
        if (startLevel != null && runSec >= maxRun / 2 && snap.sensors().dataValid()) {
            double rise = snap.sensors().waterLevel() - startLevel;
            if (rise < minRise) {
                alerts.raise(AlertType.PUMP_NO_WATER_RISE,
                        "Bơm chạy %d giây nhưng mực nước chỉ thay đổi %s%% (từ %s%% lên %s%%). Kiểm tra bơm, đường ống, nguồn nước."
                                .formatted(runSec, f(rise), f(startLevel), f(snap.sensors().waterLevel())), rise);
            } else {
                alerts.resolve(AlertType.PUMP_NO_WATER_RISE);
            }
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void checkConnectivity() {
        ConnectionStatus status = state.connectionStatus();
        int offlineSec = settings.getInt(SettingKey.GATEWAY_OFFLINE_SECONDS);
        FarmSnapshot last = state.latest();
        switch (status) {
            case OFFLINE -> {
                // Lúc mới khởi động chưa có dữ liệu: chờ hết thời gian offline rồi mới cảnh báo
                if (last == null && Duration.between(state.startedAt(), Instant.now()).toSeconds() < offlineSec) {
                    return;
                }
                alerts.raise(AlertType.GATEWAY_OFFLINE, last == null
                        ? "Chưa nhận được dữ liệu từ Gateway. Kiểm tra nguồn điện, Wi-Fi/4G và ESP32."
                        : "Không nhận được dữ liệu từ trang trại từ " + LOCAL_TIME.format(last.receivedAt())
                          + ". PLC vẫn tự động điều khiển. Kiểm tra nguồn điện, mạng và Gateway.", null);
                alerts.resolve(AlertType.PLC_LINK_LOST);
            }
            case PLC_LINK_LOST -> {
                alerts.resolve(AlertType.GATEWAY_OFFLINE);
                String err = state.plcLinkError();
                alerts.raise(AlertType.PLC_LINK_LOST, err != null
                        ? "Gateway báo không đọc được PLC qua RS485 (" + err + "). Kiểm tra dây RS485, nguồn PLC và cấu hình Modbus."
                        : "Gateway vẫn kết nối nhưng không nhận được dữ liệu mới từ PLC (heartbeat D201 đứng yên). Kiểm tra dây RS485 và PLC.", null);
            }
            case ONLINE -> {
                alerts.resolve(AlertType.GATEWAY_OFFLINE);
                alerts.resolve(AlertType.PLC_LINK_LOST);
            }
        }
    }

    private static String onOff(FarmSnapshot snap, Device d) {
        return snap.isOn(d) ? "ĐANG CHẠY" : "TẮT";
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
