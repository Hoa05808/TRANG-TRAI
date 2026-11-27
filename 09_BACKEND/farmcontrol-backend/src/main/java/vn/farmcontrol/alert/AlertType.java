package vn.farmcontrol.alert;

public enum AlertType {
    TEMP_HIGH(AlertSeverity.CRITICAL, "Nhiệt độ chuồng cao"),
    WATER_LOW(AlertSeverity.WARNING, "Mực nước bể thấp"),
    HUMIDITY_HIGH(AlertSeverity.WARNING, "Độ ẩm cao"),
    HUMIDITY_LOW(AlertSeverity.WARNING, "Độ ẩm thấp"),
    SENSOR_FAULT(AlertSeverity.WARNING, "Lỗi cảm biến"),
    PLC_ERROR(AlertSeverity.CRITICAL, "PLC báo lỗi"),
    PLC_ALARM(AlertSeverity.WARNING, "PLC báo cảnh báo"),
    PUMP_RUNTIME_EXCEEDED(AlertSeverity.WARNING, "Bơm chạy quá thời gian"),
    PUMP_NO_WATER_RISE(AlertSeverity.CRITICAL, "Bơm chạy nhưng mực nước không tăng"),
    GATEWAY_OFFLINE(AlertSeverity.CRITICAL, "Mất kết nối Gateway"),
    PLC_LINK_LOST(AlertSeverity.CRITICAL, "Mất kết nối PLC – Gateway"),
    FEED_LOW(AlertSeverity.WARNING, "Thùng thức ăn sắp hết");

    private final AlertSeverity defaultSeverity;
    private final String title;

    AlertType(AlertSeverity defaultSeverity, String title) {
        this.defaultSeverity = defaultSeverity;
        this.title = title;
    }

    public AlertSeverity defaultSeverity() { return defaultSeverity; }
    public String title() { return title; }
}
