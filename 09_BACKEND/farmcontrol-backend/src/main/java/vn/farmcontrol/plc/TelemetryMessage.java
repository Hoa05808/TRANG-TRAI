package vn.farmcontrol.plc;

/**
 * Gói telemetry ESP32 gửi lên topic farm/{farmId}/telemetry.
 * ESP32 chỉ cần đọc 3 khối thanh ghi liên tiếp bằng Modbus và gửi nguyên giá trị thô:
 *
 * <pre>
 * {"gw":"esp32-01","seq":1234,
 *  "d50":[325,750,285,650,550,1200,800,10,1,1],     // D50..D59 cảm biến
 *  "d100":[1,0,0,1,0,1,1,0,0,3600],                // D100..D109 trạng thái
 *  "d110":[0,0,0,0,0,1,1,2,1,705],                 // D110..D119 cho ăn (không bắt buộc, ESP32 cũ không gửi)
 *  "d200":[1,45,12,0,0,0,0,0,0,1]}                 // D200..D209 giao tiếp
 * </pre>
 *
 * Việc quy đổi đơn vị (×10) do backend xử lý.
 */
public record TelemetryMessage(String gw, Long seq, int[] d50, int[] d100, int[] d110, int[] d200) {

    public static final int BLOCK_SIZE = 10;

    /** Bit D114: thùng thức ăn sắp hết. */
    public static final int FEED_LOW_FISH = 1, FEED_LOW_DUCK = 2, FEED_LOW_CHICKEN = 4;

    public boolean isComplete() {
        return d50 != null && d50.length >= BLOCK_SIZE
                && d100 != null && d100.length >= BLOCK_SIZE
                && d200 != null && d200.length >= BLOCK_SIZE;
    }

    // --- D50..D59 ---
    public int coopTempRaw() { return d50[0]; }
    public int airHumidityRaw() { return d50[1]; }
    public int waterTempRaw() { return d50[2]; }
    public int waterLevelRaw() { return d50[3]; }
    public int soilHumidityRaw() { return d50[4]; }
    public int lightLux() { return d50[5]; }
    public int co2Ppm() { return d50[6]; }
    public int nh3Ppm() { return d50[7]; }
    public boolean tempSensorOk() { return d50[8] == 1; }
    public boolean sensorDataValid() { return d50[9] == 1; }

    // --- D100..D109 ---
    public boolean deviceOn(Device d) {
        int i = d.statusIndex();
        if (i < BLOCK_SIZE) return d100[i] != 0;
        return hasFeed() && d110[i - BLOCK_SIZE] != 0;
    }
    public int operationMode() { return d100[5]; }
    public int plcState() { return d100[6]; }
    public int errorCode() { return d100[7]; }
    public int alarmCode() { return d100[8]; }
    /** D109 = số phút PLC đã chạy từ lúc cấp điện; trả về giây cho app. */
    public int uptime() { return d100[9] * 60; }

    // --- D110..D119 cho ăn ---
    public boolean hasFeed() { return d110 != null && d110.length >= BLOCK_SIZE; }
    /** D114: bit 1 = thùng cá, 2 = thùng vịt, 4 = thùng gà sắp hết. */
    public int feedLowMask() { return hasFeed() ? d110[4] & 7 : 0; }
    public int fishFeedsToday() { return hasFeed() ? d110[5] : 0; }
    public int duckFeedsToday() { return hasFeed() ? d110[6] : 0; }
    public int chickenFeedsToday() { return hasFeed() ? d110[7] : 0; }
    public boolean feedEnabled() { return hasFeed() && d110[8] == 1; }
    /** D119: giờ đồng hồ PLC dạng HHMM (705 = 07:05), -1 nếu không có. */
    public int plcClockHhmm() { return hasFeed() ? d110[9] : -1; }

    // --- D200..D209 ---
    public int dataVersion() { return d200[0]; }
    public int plcHeartbeat() { return d200[1]; }
    public int gatewayHeartbeat() { return d200[2]; }
    public int commErrorCode() { return d200[7]; }
    public boolean remoteEnabled() { return d200[9] == 1; }
}
