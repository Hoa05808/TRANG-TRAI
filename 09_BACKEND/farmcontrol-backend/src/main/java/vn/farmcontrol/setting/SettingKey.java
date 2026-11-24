package vn.farmcontrol.setting;

import java.util.Arrays;
import java.util.Optional;

/**
 * Thông số phía backend (ngưỡng cảnh báo, thời gian mất kết nối).
 * Lưu ý: các ngưỡng ĐIỀU KHIỂN (D0..D9) vẫn nằm trong PLC; ở đây chỉ là ngưỡng CẢNH BÁO trên app.
 */
public enum SettingKey {
    ALERT_TEMP_HIGH("alert.temp.high", 35.0, 20, 50, "°C", "Ngưỡng cảnh báo nhiệt độ chuồng cao"),
    ALERT_TEMP_DEADBAND("alert.temp.deadband", 1.0, 0.1, 5, "°C", "Độ trễ hết cảnh báo nhiệt độ"),
    ALERT_WATER_LOW("alert.water.low", 20.0, 0, 100, "%", "Ngưỡng cảnh báo mực nước bể thấp"),
    ALERT_WATER_DEADBAND("alert.water.deadband", 5.0, 0.5, 30, "%", "Độ trễ hết cảnh báo mực nước"),
    ALERT_HUMIDITY_HIGH("alert.humidity.high", 85.0, 30, 100, "%", "Ngưỡng cảnh báo độ ẩm cao"),
    ALERT_HUMIDITY_LOW("alert.humidity.low", 40.0, 0, 90, "%", "Ngưỡng cảnh báo độ ẩm thấp"),
    ALERT_PUMP_MAX_RUN_SECONDS("alert.pump.maxRunSeconds", 300, 30, 7200, "giây", "Thời gian bơm chạy liên tục tối đa"),
    ALERT_PUMP_MIN_LEVEL_RISE("alert.pump.minLevelRise", 2.0, 0, 50, "%",
            "Mực nước tối thiểu phải tăng sau nửa thời gian bơm tối đa"),
    GATEWAY_OFFLINE_SECONDS("gateway.offlineSeconds", 30, 10, 600, "giây", "Không nhận dữ liệu sau N giây = mất kết nối"),
    PLC_HEARTBEAT_TIMEOUT_SECONDS("plc.heartbeatTimeoutSeconds", 30, 10, 600, "giây",
            "Heartbeat PLC (D201) không đổi sau N giây = mất kết nối PLC");

    private final String key;
    private final double defaultValue;
    private final double min;
    private final double max;
    private final String unit;
    private final String description;

    SettingKey(String key, double defaultValue, double min, double max, String unit, String description) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.description = description;
    }

    public String key() { return key; }
    public double defaultValue() { return defaultValue; }
    public double min() { return min; }
    public double max() { return max; }
    public String unit() { return unit; }
    public String description() { return description; }

    public static Optional<SettingKey> fromKey(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
    }
}
