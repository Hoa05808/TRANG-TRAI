package vn.farmcontrol.plc;

/**
 * Gói telemetry ESP32 gửi lên topic farm/{farmId}/telemetry.
 * ESP32 chỉ cần đọc 3 khối thanh ghi liên tiếp bằng Modbus và gửi nguyên giá trị thô:
 *
 * <pre>
 * {"gw":"esp32-01","seq":1234,
 *  "d50":[325,750,285,650,550,1200,800,10,1,1],     // D50..D59 cảm biến
 *  "d100":[1,0,0,1,0,1,1,0,0,3600],                // D100..D109 trạng thái
 *  "d200":[1,45,12,0,0,0,0,0,0,1]}                 // D200..D209 giao tiếp
 * </pre>
 *
 * Việc quy đổi đơn vị (×10) do backend xử lý.
 */
public record TelemetryMessage(String gw, Long seq, int[] d50, int[] d100, int[] d200) {

    public static final int BLOCK_SIZE = 10;

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
    public boolean deviceOn(Device d) { return d100[d.statusIndex()] != 0; }
    public int operationMode() { return d100[5]; }
    public int plcState() { return d100[6]; }
    public int errorCode() { return d100[7]; }
    public int alarmCode() { return d100[8]; }
    public int uptime() { return d100[9]; }

    // --- D200..D209 ---
    public int dataVersion() { return d200[0]; }
    public int plcHeartbeat() { return d200[1]; }
    public int gatewayHeartbeat() { return d200[2]; }
    public int commErrorCode() { return d200[7]; }
    public boolean remoteEnabled() { return d200[9] == 1; }
}
