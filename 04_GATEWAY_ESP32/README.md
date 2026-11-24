# FarmControl Gateway – ESP32 + RS485 + MQTT

PLC FX3U ⇄ (RS485 / Modbus RTU) ⇄ **ESP32** ⇄ (Wi-Fi / MQTT) ⇄ Mosquitto ⇄ Backend

Hợp đồng dữ liệu: `05_IOT_MQTT/MQTT_CONTRACT.md` · Sửa Ladder: `01_GX_WORKS2/LADDER_D210_HANDSHAKE.md`

## Đấu dây

| ESP32 | Module RS485 (MAX485 / loại cách ly) |
|---|---|
| GPIO17 (TX2) | DI |
| GPIO16 (RX2) | RO |
| GPIO4 | DE + RE (nối chung) |
| 3V3 hoặc 5V (theo module) | VCC |
| GND | GND |

Module RS485 A/B → cổng RS485 của **FX3U-485ADP-MB** (SDA/RDA nối A, SDB/RDB nối B khi đi 2 dây; nối SG chung).
Đường dây dài nên dùng cáp xoắn có chống nhiễu và điện trở đầu cuối 120 Ω ở hai đầu.
Nên dùng module RS485 **có cách ly** vì tủ điện có contactor, motor gây nhiễu.

## Cấu hình

1. `include/mqtt_secrets.h`: đã được `05_IOT_MQTT/setup-mosquitto.ps1` tạo sẵn (IP máy tính + tài khoản `esp32-01`).
2. `include/wifi_secrets.h`: **điền tên và mật khẩu Wi-Fi** (đang là giá trị mẫu `TEN_WIFI`).
3. `include/config.h`: tốc độ Modbus, parity, địa chỉ slave phải khớp cấu hình adapter Modbus trong PLC.

Các file `*_secrets.h` nằm trong `.gitignore`.

## Biên dịch và nạp

VS Code (đã cài extension PlatformIO): mở thư mục `04_GATEWAY_ESP32`, bấm ✓ (Build) rồi → (Upload) ở thanh dưới cùng.

Hoặc dòng lệnh:

```powershell
cd C:\Users\PC\Documents\GitHub\TRANG-TRAI\04_GATEWAY_ESP32
python -m platformio run -t upload
python -m platformio device monitor
```

Log khi chạy đúng:

```
[MQTT] Đã kết nối, đã đăng ký nhận lệnh
```

Nếu PLC chưa nối, log sẽ báo `[PLC] Đọc lỗi ... Modbus timeout` và app hiện **Mất kết nối PLC – Gateway**. Như vậy là đúng.

## Thử khi chưa có ESP32

```powershell
python C:\Users\PC\Documents\GitHub\TRANG-TRAI\05_IOT_MQTT\esp32_emulator.py
```

Giả lập ESP32 + PLC qua MQTT thật (có bắt tay D210). Thêm `--plc-down` để giả lập đứt RS485.
Backend phải chạy với `GATEWAY_MODE=mqtt`.
