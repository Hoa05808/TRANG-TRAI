// FarmControl Gateway - cấu hình phần cứng và giao thức.
// Hợp đồng MQTT: 05_IOT_MQTT/MQTT_CONTRACT.md
#pragma once

// ---------- Định danh ----------
#define FARM_ID     "farm01"     // phải khớp FARM_ID của backend
#define GATEWAY_ID  "esp32-01"   // phải khớp tài khoản MQTT và ACL

// ---------- RS485 (module MAX485 / module RS485 cách ly) ----------
// ESP32 GPIO17 (TX2) -> DI, GPIO16 (RX2) <- RO, GPIO4 -> DE + RE (nối chung)
#define RS485_RX_PIN     16
#define RS485_TX_PIN     17
#define RS485_DE_RE_PIN  4

// ---------- Modbus RTU tới PLC FX3U ----------
// FX3U cần adapter Modbus FX3U-485ADP-MB (FX3U-485-BD KHÔNG hỗ trợ Modbus RTU).
// Tốc độ / khung truyền / địa chỉ slave PHẢI khớp tham số trong PLC (D8400, D8411, D8414 cho kênh 1).
#define MODBUS_BAUD      9600
#define MODBUS_CONFIG    SERIAL_8E1   // 8 bit dữ liệu, parity chẵn, 1 stop bit
#define MODBUS_SLAVE_ID  1
// Địa chỉ holding register tương ứng D0 (adapter FX3U-485ADP-MB mặc định: D0 = 0)
#define MODBUS_D_OFFSET  0

// ---------- Chu kỳ ----------
#define TELEMETRY_INTERVAL_MS   2000   // đọc PLC + gửi telemetry mỗi 2 giây
#define PLC_FAIL_BEFORE_REPORT  2      // đọc lỗi liên tiếp N lần thì báo plcOk=false
#define CMD_POLL_INTERVAL_MS    200    // hỏi D205/D206 khi đang chờ PLC xử lý lệnh
#define CMD_PLC_TIMEOUT_MS      8000   // PLC không xác nhận trong 8 giây -> báo lỗi (backend chờ 15 giây)
#define MQTT_RETRY_MS           5000

// ---------- Thanh ghi PLC (xem 02_IO_LIST và 08_DOCUMENTATION) ----------
#define REG_SENSORS      50    // D50..D59
#define REG_STATUS       100   // D100..D109
#define REG_COMM         200   // D200..D209
#define REG_GW_HEARTBEAT 202   // ESP32 ghi bộ đếm heartbeat Gateway
#define REG_CMD_ID       203   // ESP32 ghi mã lệnh
#define REG_CMD_VALUE    204   // ESP32 ghi giá trị lệnh
#define REG_ACK_REQ      205   // PLC ghi mã yêu cầu đã xử lý
#define REG_CMD_RESULT   206   // PLC ghi kết quả 1/2/3/4
#define REG_REQ_ID       210   // ESP32 ghi mã yêu cầu (reqId) - GHI SAU CÙNG
#define BLOCK_SIZE       10
