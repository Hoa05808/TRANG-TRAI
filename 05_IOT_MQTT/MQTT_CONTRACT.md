# Hợp đồng MQTT giữa ESP32 Gateway và Backend

Tài liệu này là "hợp đồng" mà firmware ESP32 (giai đoạn 2) và backend Spring Boot (giai đoạn 3) cùng tuân theo.
`{farmId}` mặc định là `farm01` (biến môi trường `FARM_ID` của backend).

| Topic | Chiều | QoS | Retained | Nội dung |
|---|---|---|---|---|
| `farm/{farmId}/telemetry` | ESP32 → Backend | 0 | Không | Dữ liệu thanh ghi PLC, gửi mỗi 2–5 giây |
| `farm/{farmId}/command` | Backend → ESP32 | 1 | **Không bao giờ** | Lệnh điều khiển |
| `farm/{farmId}/command/ack` | ESP32 → Backend | 1 | Không | Kết quả xử lý lệnh |
| `farm/{farmId}/gateway/status` | ESP32 → Backend | 1 | Có | `online` / `offline` (đặt làm Last Will) |

## 1. Telemetry

ESP32 đọc 3 khối thanh ghi liên tiếp bằng Modbus (mỗi khối 10 thanh ghi) và gửi **giá trị thô**, backend tự quy đổi (÷10).

```json
{
  "gw": "esp32-01",
  "seq": 1234,
  "d50":  [325, 750, 285, 650, 550, 1200, 800, 10, 1, 1],
  "d100": [1, 0, 0, 1, 0, 1, 1, 0, 0, 3600],
  "d200": [1, 45, 12, 0, 0, 0, 0, 0, 0, 1]
}
```

| Khối | Vị trí | Thanh ghi | Ý nghĩa |
|---|---|---|---|
| d50 | 0–9 | D50–D59 | Nhiệt độ chuồng, độ ẩm, nhiệt độ nước, mực nước, độ ẩm đất, ánh sáng, CO₂, NH₃, trạng thái cảm biến nhiệt (1 = OK), dữ liệu hợp lệ (1 = OK) |
| d100 | 0–4 | D100–D104 | Bơm, quạt 1, quạt 2, đèn, van (0/1) |
| d100 | 5–9 | D105–D109 | Chế độ (0 Stop/1 Auto/2 Manual), trạng thái PLC (0 Stop/1 Run/2 Error), mã lỗi, mã cảnh báo, uptime |
| d200 | 0–9 | D200–D209 | Phiên bản, **heartbeat PLC (D201)**, heartbeat gateway, D203…D208, **cho phép REMOTE (D209)** |

- Backend coi **mất kết nối Gateway** khi không nhận telemetry quá 30 giây.
- Backend coi **mất kết nối PLC–Gateway** khi D201 không đổi quá 30 giây. PLC phải tăng D201 đều đặn (ví dụ mỗi giây).
- Nếu ESP32 không đọc được PLC, **không gửi** số liệu cũ. Thay vào đó gửi lên cùng topic:
  ```json
  {"gw": "esp32-01", "plcOk": false, "err": "Modbus timeout - PLC không trả lời"}
  ```
  Backend báo **Mất kết nối PLC – Gateway** ngay lập tức (không chờ heartbeat), thiết bị hiện KHÔNG RÕ, chặn gửi lệnh.
- Thư viện PubSubClient trên ESP32 chỉ publish QoS 0; trong mạng LAN điều này chấp nhận được (backend có TIMEOUT cho lệnh).

## 2. Lệnh (Backend → ESP32)

```json
{"reqId": 1234, "cmd": 1, "val": 0, "exp": 1732450010, "ts": 1732450000}
```

- `reqId`: 1–32767, **mỗi lệnh một số khác nhau** (kể cả khi gửi lại cùng một `cmd`).
- `cmd`: mã lệnh D203 (1 Bật bơm, 2 Tắt bơm, 3 Bật quạt, 4 Tắt quạt, 5 Bật đèn, 6 Tắt đèn, 7 Mở van, 8 Đóng van).
- `exp`: epoch giây. **ESP32 phải bỏ lệnh nếu giờ hiện tại > exp** và gửi ack `result = 10`. ESP32 cần đồng bộ giờ bằng NTP.
- Backend chỉ gửi một lệnh tại một thời điểm, và không bao giờ tự gửi lại lệnh khi mất kết nối.

## 3. Phản hồi lệnh (ESP32 → Backend)

```json
{"reqId": 1234, "cmd": 1, "result": 3, "msg": "tùy chọn"}
```

| result | Ý nghĩa | Nguồn |
|---|---|---|
| 1 | PLC đã chấp nhận | D206 |
| 2 | PLC từ chối (liên động, sai chế độ) | D206 |
| 3 | PLC đã thực hiện | D206 |
| 4 | Lỗi thực hiện | D206 |
| 10 | Lệnh hết hạn khi tới Gateway, không thực hiện | ESP32 |
| 11 | Lỗi RS485 / không ghi được xuống PLC | ESP32 |

Không phản hồi trong 15 giây → backend đánh dấu `TIMEOUT` và báo người dùng.

## 4. Đề xuất sửa giao thức lệnh trong PLC (vấn đề D203 / D210)

Vấn đề hiện tại: PLC so sánh `D203 ≠ D210` để phát hiện lệnh mới, nên gửi lại **cùng mã lệnh** sẽ không được nhận.
Đề xuất bắt tay bằng **mã yêu cầu** thay vì mã lệnh:

| Thanh ghi | Ai ghi | Nội dung |
|---|---|---|
| D203 | ESP32 | Mã lệnh (`cmd`) |
| D204 | ESP32 | Giá trị lệnh (`val`) |
| **D210** | ESP32 | **Mã yêu cầu (`reqId`)** – ghi SAU CÙNG |
| D205 | PLC | Mã yêu cầu PLC đã xử lý (= D210 khi xong) |
| D206 | PLC | Kết quả 1/2/3/4 |

Trình tự:
1. ESP32 ghi D203, D204 rồi mới ghi D210 = reqId.
2. PLC phát hiện `D210 ≠ D205` (và D210 ≠ 0) → xử lý lệnh trong D203 qua logic liên động → ghi D206 = kết quả, rồi D205 = D210.
3. ESP32 đọc thấy `D205 = reqId` → gửi ack với D206 → ghi D203 = 0.
4. Vì reqId luôn khác nhau, gửi lại cùng mã lệnh vẫn được PLC xử lý.

Khi PLC vừa khởi động: sao chép D210 vào D205 ở xung đầu tiên (M8002) để không thực hiện lại lệnh cũ còn trong thanh ghi.

## 5. Bảo mật broker (Mosquitto)

- Bắt buộc `allow_anonymous false`, mỗi bên một tài khoản (`backend`, `esp32-01`).
- ACL: ESP32 chỉ được publish `telemetry`, `command/ack`, `gateway/status` và subscribe `command` của trang trại mình.
- Khi đưa lên Internet dùng TLS cổng 8883 (`ssl://` ở backend). Không mở PLC ra Internet.
- Xem file mẫu `mosquitto.conf` và `acl.conf` trong thư mục này.
