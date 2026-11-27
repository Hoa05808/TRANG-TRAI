package vn.farmcontrol.plc;

/**
 * Thiết bị đầu ra của PLC. statusIndex = vị trí trạng thái:
 * 0..9 trong khối D100..D109, 10..19 trong khối D110..D119 (cho ăn).
 */
public enum Device {
    PUMP_1(0, "Y0", "Máy bơm nước"),
    FAN_1(1, "Y1", "Quạt thông gió 1"),
    FAN_2(2, "Y2", "Quạt thông gió 2"),
    LIGHT_1(3, "Y3", "Đèn chiếu sáng"),
    VALVE_1(4, "Y4", "Van nước máng gà"),
    FISH_FEEDER(10, "Y10", "Máy cho cá ăn"),
    FISH_VALVE(11, "Y11", "Van thức ăn cá"),
    DUCK_VALVE(12, "Y12", "Van thức ăn vịt"),
    CHICKEN_VALVE(13, "Y13", "Van thức ăn gà");

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
    /** true nếu là van (hiển thị MỞ/ĐÓNG thay vì BẬT/TẮT). */
    public boolean isValve() { return name().endsWith("VALVE") || this == VALVE_1; }
}
