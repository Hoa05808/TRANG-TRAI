package vn.farmcontrol.plc;

/** Thiết bị đầu ra của PLC. index = vị trí trong khối D100..D104. */
public enum Device {
    PUMP_1(0, "Y0", "Máy bơm nước"),
    FAN_1(1, "Y1", "Quạt thông gió 1"),
    FAN_2(2, "Y2", "Quạt thông gió 2"),
    LIGHT_1(3, "Y3", "Đèn chiếu sáng"),
    VALVE_1(4, "Y4", "Van cấp nước");

    private final int statusIndex;
    private final String plcOutput;
    private final String displayName;

    Device(int statusIndex, String plcOutput, String displayName) {
        this.statusIndex = statusIndex;
        this.plcOutput = plcOutput;
        this.displayName = displayName;
    }

    public int statusIndex() { return statusIndex; }
    public String plcOutput() { return plcOutput; }
    public String displayName() { return displayName; }
}
