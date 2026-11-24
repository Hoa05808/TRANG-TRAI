# Sửa REMOTE COMMAND HANDLER – bắt tay bằng mã yêu cầu D210

Mục tiêu: PLC nhận được **cả khi gửi lại cùng một mã lệnh** (ví dụ bật bơm 2 lần liên tiếp),
không thực hiện lại lệnh cũ sau khi mất điện / mất kết nối, và báo kết quả rõ ràng cho app.

> Đây là đoạn mẫu để bạn đưa vào GX Works2 (chế độ List/IL hoặc vẽ lại bằng Ladder).
> Claude không mở được file `.gxw`, nên **bạn tự nhập và kiểm tra bằng mô phỏng GX Simulator trước khi nạp xuống PLC thật**.

## 1. Quy ước thanh ghi (khớp firmware ESP32 và backend)

| Thanh ghi | Ai ghi | Ý nghĩa |
|---|---|---|
| D203 | ESP32 | Mã lệnh: 1 bật bơm, 2 tắt bơm, 3 bật quạt, 4 tắt quạt, 5 bật đèn, 6 tắt đèn, 7 mở van, 8 đóng van |
| D204 | ESP32 | Giá trị lệnh (dự phòng, hiện = 0) |
| **D210** | ESP32 | **Mã yêu cầu reqId (1..32767)** – ghi SAU CÙNG, mỗi lệnh một số khác nhau |
| D205 | PLC | Mã yêu cầu PLC đã xử lý xong (= D210) |
| D206 | PLC | Kết quả: 2 = từ chối, 3 = đã thực hiện, 4 = lỗi |
| D209 | PLC | 1 = cho phép điều khiển từ xa |
| D201 | PLC | Heartbeat PLC, tăng đều (ví dụ mỗi giây) |
| D202 | ESP32 | Heartbeat Gateway |

Bit nội bộ dùng thêm (vùng M170–M199 còn trống): **M181** = có yêu cầu mới, **M182** = yêu cầu hợp lệ,
**M183** = bị liên động chặn.

**Xóa logic cũ** so sánh `D203 <> D210` và mọi lệnh ghi vào D210 trong PLC (ví dụ `MOV D203 D210`):
từ giờ chỉ ESP32 được ghi D210.

## 2. Đoạn chương trình (IL – FX3U)

```
; ---------- Khởi động: bỏ qua yêu cầu cũ còn trong thanh ghi ----------
LD     M8002
MOV    D210  D205
MOV    K0    D206
MOV    K0    D203

; ---------- Có yêu cầu mới: D210 <> D205 và D210 <> 0 ----------
LD<>   D210  D205
AND<>  D210  K0
OUT    M181

; ---------- Hợp lệ: REMOTE cho phép, không dừng khẩn, mã 1..8 ----------
LD     M181
AND=   D209  K1
ANI    M3                 ; EMERGENCY_ACTIVE
AND>=  D203  K1
AND<=  D203  K8
OUT    M182

; ---------- Không hợp lệ -> từ chối ----------
LD     M181
ANI    M182
MOV    K2    D206
MOV    D210  D205

; ---------- Giải mã thành yêu cầu từ xa (xung 1 chu kỳ) ----------
LD     M182
AND=   D203  K1
OUT    M170               ; REMOTE_PUMP_START
LD     M182
AND=   D203  K2
OUT    M171               ; REMOTE_PUMP_STOP
LD     M182
AND=   D203  K3
OUT    M172               ; REMOTE_FAN_START
LD     M182
AND=   D203  K4
OUT    M173               ; REMOTE_FAN_STOP
LD     M182
AND=   D203  K5
OUT    M174               ; REMOTE_LIGHT_ON
LD     M182
AND=   D203  K6
OUT    M175               ; REMOTE_LIGHT_OFF
LD     M182
AND=   D203  K7
OUT    M176               ; REMOTE_VALVE_OPEN
LD     M182
AND=   D203  K8
OUT    M177               ; REMOTE_VALVE_CLOSE

; ---------- Lệnh BẬT bị liên động chặn? (lệnh TẮT luôn cho phép) ----------
LD     M182
LD=    D203  K1
ANI    M29                ; PUMP_INTERLOCK_OK
LD=    D203  K3
ANI    M49                ; FAN_INTERLOCK_OK
ORB
LD=    D203  K5
ANI    M67                ; LIGHT_INTERLOCK_OK
ORB
LD=    D203  K7
ANI    M87                ; VALVE_INTERLOCK_OK
ORB
ANB
OUT    M183

; ---------- Ghi kết quả và xác nhận ----------
LD     M182
AND    M183
MOV    K2    D206         ; từ chối do liên động
LD     M182
ANI    M183
MOV    K3    D206         ; đã thực hiện
LD     M182
MOV    D210  D205         ; báo ESP32: đã xử lý reqId này
```

Cách hoạt động trong 1 chu kỳ quét:
1. ESP32 ghi D203, D204 rồi D210. PLC thấy `D210 ≠ D205` nên M181 = 1.
2. Hợp lệ thì một trong M170..M177 lên 1 trong đúng 1 chu kỳ. Logic điều khiển hiện có (M22, M42, M62, M82…)
   dùng các bit này **qua liên động an toàn** như trước; không kích trực tiếp Y.
3. PLC ghi kết quả vào D206 rồi `D205 = D210`. Chu kỳ sau M181 = 0, nên lệnh không lặp lại.
4. ESP32 đọc thấy `D205 = reqId`, gửi kết quả lên app, rồi ghi `D203 = 0`.
5. Lệnh kế tiếp có reqId mới, kể cả khi cùng mã lệnh, nên PLC vẫn nhận.

Kiểm tra liên động: M29 / M49 / M67 / M87 phải được tính **trước** đoạn này trong chương trình.

## 3. Heartbeat PLC (D201)

Backend coi PLC mất liên lạc nếu D201 đứng yên quá 30 giây. Ví dụ tăng mỗi giây bằng xung M8013:

```
LD     M8013
PLS    M190
LD     M190
INC    D201
```

## 4. Cấu hình Modbus RTU trên PLC

- FX3U cần adapter **FX3U-485ADP-MB** (lắp bên trái PLC, thường qua board FX3U-CNV-BD). FX3U-485-BD **không** hỗ trợ Modbus RTU.
- Đặt PLC làm **Modbus slave**, địa chỉ trạm **1**, tốc độ **9600**, **8 bit, parity chẵn (Even), 1 stop bit**
  bằng các thanh ghi đặc biệt của kênh (D8400… cho kênh 1) theo sổ tay *FX3S/FX3G/FX3GC/FX3U/FX3UC Series User's Manual – MODBUS Serial Communication Edition*.
- Nếu đặt khác, sửa `MODBUS_BAUD`, `MODBUS_CONFIG`, `MODBUS_SLAVE_ID` trong `04_GATEWAY_ESP32/include/config.h` cho khớp.
- Mặc định adapter: holding register 0 = D0, nên D50 là địa chỉ 50 (`MODBUS_D_OFFSET = 0`).

## 5. Thử từng bước

1. GX Simulator: ghi tay D203 = 1, rồi D210 = 1 → kiểm tra M170 lên một chu kỳ, D206 = 3, D205 = 1.
2. Ghi lại D203 = 1, D210 = 2 → PLC phải xử lý lần nữa (đây chính là lỗi cũ đã sửa).
3. Đặt D209 = 0 rồi ghi D210 = 3 → D206 = 2 (từ chối).
4. Nạp xuống PLC thật, nối ESP32, thử từ app.
