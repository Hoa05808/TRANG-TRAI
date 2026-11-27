package vn.farmcontrol.plc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lệnh điều khiển từ xa, khớp với bảng mã D203 REMOTE_COMMAND_ID trong PLC.
 * expected: trạng thái thiết bị mong đợi sau khi PLC thực hiện (dùng để xác nhận bằng telemetry).
 */
public enum CommandType {
    PUMP_ON(1, "Bật bơm", Map.of(Device.PUMP_1, true)),
    PUMP_OFF(2, "Tắt bơm", Map.of(Device.PUMP_1, false)),
    FAN_ON(3, "Bật quạt", Map.of(Device.FAN_1, true)),
    FAN_OFF(4, "Tắt quạt", Map.of(Device.FAN_1, false, Device.FAN_2, false)),
    LIGHT_ON(5, "Bật đèn", Map.of(Device.LIGHT_1, true)),
    LIGHT_OFF(6, "Tắt đèn", Map.of(Device.LIGHT_1, false)),
    // Van nước máng gà: PLC mở D414 giây rồi tự đóng (cùng lịch giờ với cho gà ăn)
    VALVE_OPEN(7, "Mở van nước máng gà", Map.of(Device.VALVE_1, true)),
    VALVE_CLOSE(8, "Đóng van nước máng gà", Map.of(Device.VALVE_1, false)),
    // Cho ăn: PLC tự chạy hết chu kỳ theo thời gian D410..D413 rồi tự tắt
    FEED_FISH_START(9, "Cho cá ăn ngay", Map.of(Device.FISH_FEEDER, true)),
    FEED_FISH_STOP(10, "Dừng cho cá ăn", Map.of(Device.FISH_FEEDER, false, Device.FISH_VALVE, false)),
    FEED_DUCK_START(11, "Cho vịt ăn ngay", Map.of(Device.DUCK_VALVE, true)),
    FEED_DUCK_STOP(12, "Dừng cho vịt ăn", Map.of(Device.DUCK_VALVE, false)),
    FEED_CHICKEN_START(13, "Cho gà ăn ngay", Map.of(Device.CHICKEN_VALVE, true)),
    FEED_CHICKEN_STOP(14, "Dừng cho gà ăn", Map.of(Device.CHICKEN_VALVE, false));

    private final int plcCode;
    private final String displayName;
    private final Map<Device, Boolean> expected;

    CommandType(int plcCode, String displayName, Map<Device, Boolean> expected) {
        this.plcCode = plcCode;
        this.displayName = displayName;
        this.expected = expected;
    }

    public int plcCode() { return plcCode; }
    public String displayName() { return displayName; }
    public Map<Device, Boolean> expected() { return expected; }

    public boolean affects(Device device) {
        return expected.containsKey(device);
    }

    public List<Device> devices() {
        return List.copyOf(expected.keySet());
    }

    public static Optional<CommandType> fromPlcCode(int code) {
        return Arrays.stream(values()).filter(c -> c.plcCode == code).findFirst();
    }
}
