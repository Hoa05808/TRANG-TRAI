# FarmControl Backend – Giai đoạn 3

Java 21 · Spring Boot 4.1 · MySQL 8 · JWT · MQTT (Paho) · WebSocket STOMP

```
Flutter ──HTTPS/REST + WebSocket──► Spring Boot ──MQTT──► Mosquitto ──► ESP32 ──RS485──► PLC FX3U
                                        │
                                      MySQL
```

## 1. Chạy backend

Yêu cầu: JDK 21, MySQL 8 đang chạy. Không cần cài Maven (đã có `mvnw`).

1. Tạo CSDL bằng **một file duy nhất** `08_DOCUMENTATION/FARM_AUTOMATION_mysql.sql`
   (mở bằng MySQL Workbench và chạy, hoặc lệnh dưới). File dùng `CREATE TABLE IF NOT EXISTS` nên chạy lại không mất dữ liệu.

```powershell
& "C:\Program Files\MySQL\MySQL Shell 8.0\bin\mysqlsh.exe" --sql -u root -h localhost -p -f "C:\Users\PC\Documents\GitHub\TRANG-TRAI\08_DOCUMENTATION\FARM_AUTOMATION_mysql.sql"
```

2. Chạy backend:

```powershell
cd 09_BACKEND\farmcontrol-backend
$env:OWNER_PASSWORD = "mat-khau-chu-trang-trai"
.\mvnw.cmd spring-boot:run
```

- Backend kết nối CSDL `FARM_AUTOMATION` bằng `root`; mật khẩu MySQL đặt trong file `farmcontrol-backend/local.yml` (tạo tay, không commit):
  ```yaml
  spring:
    datasource:
      password: "mat-khau-mysql"
  ```
- Backend **không tự tạo bảng** (`ddl-auto: validate`): nếu bảng trong file SQL lệch với entity Java, backend báo lỗi khi khởi động.
  Các bảng backend dùng nằm ở mục 10 của file SQL.
- Lần chạy đầu tạo tài khoản `owner` với mật khẩu `OWNER_PASSWORD`. Nếu không đặt, mật khẩu được sinh ngẫu nhiên và in **một lần** trong log.
- Mặc định `GATEWAY_MODE=simulator`: backend tự giả lập ESP32 + PLC để làm app Flutter khi chưa có phần cứng.
- Mở bằng IntelliJ: *Open* thư mục `farmcontrol-backend`, chạy `FarmControlApplication`, thêm biến môi trường ở *Run Configuration*.

### Biến môi trường

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/FARM_AUTOMATION?...` | |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / trống | Mật khẩu MySQL đặt trong `local.yml` (không commit) |
| `JWT_SECRET` | khóa dev | **Bắt buộc đổi** khi chạy thật, ≥ 32 ký tự |
| `OWNER_USERNAME` / `OWNER_PASSWORD` | `owner` / tự sinh | Chỉ dùng khi DB chưa có tài khoản |
| `GATEWAY_MODE` | `simulator` | `mqtt` = ESP32 thật, `simulator`, `none` |
| `FARM_ID` | `farm01` | Phần `{farmId}` của topic MQTT |
| `MQTT_URL` | `tcp://localhost:1883` | Chạy thật dùng `ssl://...:8883` |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | trống | |

### Test

```powershell
.\mvnw.cmd test
```

Test tích hợp dùng H2 (chế độ MySQL), kiểm tra: đăng nhập, phân quyền, refresh token, vòng đời lệnh,
xác nhận lệnh bằng telemetry, ghi lịch sử người điều khiển, gửi lại cùng mã lệnh, cảnh báo có độ trễ, thông số.

## 2. Phân quyền

| Vai trò | Xem | Điều khiển, xác nhận cảnh báo | Thông số, tài khoản |
|---|---|---|---|
| `OWNER` (chủ trang trại) | ✔ | ✔ | ✔ |
| `OPERATOR` (vận hành) | ✔ | ✔ | ✘ |
| `VIEWER` (chỉ xem) | ✔ | ✘ | ✘ |

## 3. REST API cho Flutter

Mọi API (trừ đăng nhập) cần header `Authorization: Bearer <accessToken>`.
Lỗi luôn có dạng `{"code": "MA_LOI", "message": "Thông báo tiếng Việt", "timestamp": "..."}`.
Thời gian dùng ISO-8601 UTC (`2026-11-24T07:30:00Z`); app tự đổi sang giờ Việt Nam.

### Đăng nhập
| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| POST | `/api/auth/login` | `{"username","password"}` → `accessToken` (30 phút), `refreshToken` (30 ngày), `user` |
| POST | `/api/auth/refresh` | `{"refreshToken"}` → cặp token mới; token cũ bị vô hiệu |
| POST | `/api/auth/logout` | `{"refreshToken"}` |
| GET | `/api/me` | Thông tin tài khoản |
| POST | `/api/me/password` | `{"currentPassword","newPassword"}` |

Sai mật khẩu 5 lần liên tiếp → khóa đăng nhập 5 phút (`429 TOO_MANY_ATTEMPTS`).

### 01 Dashboard · 02 Điều khiển · 03 Cảm biến
| GET | `/api/dashboard` | Kết nối, cảm biến, trạng thái 5 thiết bị, chế độ, PLC, số cảnh báo |
|---|---|---|

- `connection`: `ONLINE` / `PLC_LINK_LOST` / `OFFLINE`.
- Khi không `ONLINE`: `devices[].state = UNKNOWN`, `mode/plcState = UNKNOWN`. App hiển thị xám, **không** giả định thiết bị đang chạy.
- `remoteEnabled = false` → app khóa nút điều khiển.

### Lệnh điều khiển
| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/commands/types` | Danh sách lệnh: `PUMP_ON, PUMP_OFF, FAN_ON, FAN_OFF, LIGHT_ON, LIGHT_OFF, VALVE_OPEN, VALVE_CLOSE` |
| POST | `/api/commands` | `{"type":"PUMP_ON"}` → `202`, trạng thái `SENT` |
| GET | `/api/commands/{id}` | Trạng thái lệnh |

Vòng đời: `SENT → ACCEPTED → EXECUTED` (+ `confirmedAt` khi telemetry xác nhận thiết bị đã đổi trạng thái),
hoặc `REJECTED` / `FAILED` / `EXPIRED` / `TIMEOUT`. `BLOCKED` = backend chặn trước khi gửi.

Mã lỗi khi gửi lệnh (`409`): `GATEWAY_OFFLINE`, `PLC_LINK_LOST`, `BROKER_DISCONNECTED`, `PLC_NOT_RUNNING`,
`REMOTE_DISABLED`, `COMMAND_IN_PROGRESS`. `503 GATEWAY_UNAVAILABLE` khi không gửi được tới broker.

### 04 Biểu đồ
| GET | `/api/sensors/history?range=HOUR\|DAY\|WEEK\|MONTH` | Điểm trung bình/min/max theo khoảng; có thể dùng `from`, `to`, `bucketMinutes` |
|---|---|---|
| GET | `/api/history/device-runtime?from=&to=` | Thời gian chạy, số lần khởi động và các đoạn chạy của từng thiết bị |

### 05 Cảnh báo
| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/alerts/active` | Cảnh báo đang diễn ra |
| GET | `/api/alerts?from=&to=&status=&severity=&page=&size=` | Lịch sử cảnh báo |
| POST | `/api/alerts/{id}/ack` | Xác nhận đã biết (OWNER, OPERATOR) |

Loại cảnh báo: `TEMP_HIGH`, `WATER_LOW`, `HUMIDITY_HIGH`, `HUMIDITY_LOW`, `SENSOR_FAULT`, `PLC_ERROR`, `PLC_ALARM`,
`PUMP_RUNTIME_EXCEEDED`, `PUMP_NO_WATER_RISE`, `GATEWAY_OFFLINE`, `PLC_LINK_LOST`.
Mức độ: `INFO` (xanh), `WARNING` (vàng), `CRITICAL` (đỏ).

### 06 Lịch sử
| GET | `/api/history/commands?from=&to=&status=&page=&size=` | Ai gửi lệnh gì, lúc nào, kết quả |
|---|---|---|
| GET | `/api/history/device-events?device=PUMP_1&from=&to=` | Thiết bị bật/tắt; `source = REMOTE` (kèm `username`) hoặc `PLC` (tự động / tại tủ) |

### 07 Cài đặt
| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/settings` | Ngưỡng cảnh báo kèm min/max/đơn vị |
| PUT | `/api/settings` | `{"alert.temp.high": 36}` (OWNER) |
| GET/POST | `/api/users` | Danh sách / tạo tài khoản (OWNER) |
| PATCH | `/api/users/{id}` | `{"fullName","role","enabled"}` (OWNER) |
| POST | `/api/users/{id}/password` | Đặt lại mật khẩu (OWNER) |
| POST | `/api/push-tokens` | `{"token","platform":"ANDROID\|IOS"}` – token FCM (giai đoạn 6) |

> Ngưỡng ở đây là ngưỡng **cảnh báo** trên app. Ngưỡng **điều khiển** (D0–D9) vẫn nằm trong PLC và chưa cho sửa từ xa.

### Chỉ có ở chế độ simulator
`PUT /api/simulator/fault?value=TEMP_HIGH` (OWNER) — giá trị: `NONE, GATEWAY_OFFLINE, PLC_LINK_LOST, PLC_ERROR, SENSOR_FAULT, WATER_LOW, TEMP_HIGH`.

## 4. WebSocket thời gian thực

- Endpoint STOMP: `ws://<host>:8080/ws` (gói Flutter `stomp_dart_client`).
- Khung `CONNECT` kèm header `Authorization: Bearer <accessToken>`.
- Subscribe:
  - `/topic/dashboard` – giống `GET /api/dashboard`, mỗi lần có telemetry hoặc đổi trạng thái kết nối
  - `/topic/alerts` – cảnh báo mới / cập nhật / hết
  - `/topic/commands` – tiến trình lệnh

## 5. MQTT với ESP32

Xem [`05_IOT_MQTT/MQTT_CONTRACT.md`](../05_IOT_MQTT/MQTT_CONTRACT.md) – định dạng telemetry, lệnh, phản hồi, và đề xuất sửa bắt tay D203/D210 trong PLC.

## 6. Nguyên tắc an toàn đã áp dụng

- PLC luôn là nơi quyết định cuối cùng; backend chỉ gửi **yêu cầu**.
- Một lệnh tại một thời điểm, mỗi lệnh có `reqId` riêng và hạn `exp`; không bao giờ gửi lại tự động; MQTT không retained.
- Mất kết nối → trạng thái `UNKNOWN`, chặn gửi lệnh, cảnh báo `GATEWAY_OFFLINE` / `PLC_LINK_LOST`.
- Lưu người gửi lệnh và kết quả; lịch sử thiết bị phân biệt `REMOTE` / `PLC`.
- Mật khẩu BCrypt, refresh token lưu dạng SHA-256 và xoay vòng, khóa tạm khi đoán mật khẩu.

## 7. Còn lại cho các giai đoạn sau

- Xác nhận thiết bị chạy thật: hiện `confirmedAt` dựa trên D100–D104 (trạng thái ngõ ra Y). Khi lắp tiếp điểm phụ contactor,
  thêm thanh ghi phản hồi và so sánh với lệnh → cảnh báo "Quạt không phản hồi".
- Giai đoạn 6: gửi FCM (cài đặt `NotificationService.PushSender`), Telegram làm kênh phụ.
- Triển khai: HTTPS (reverse proxy), TLS cho MQTT, sao lưu MySQL.
