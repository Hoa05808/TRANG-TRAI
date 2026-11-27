# Danh sách phần cứng cần mua (26/11/2026)

Lập theo chương trình `01_GX_WORKS2/MAIN_FIXED_IL.txt` và `02_IO_LIST/IO.txt`.
Cột "Bắt buộc": **Có** = thiếu thì chương trình không chạy đủ; *Tùy chọn* = nên có.
Kiểm tra lại mã hàng với nhà cung cấp trước khi mua.

## 1. PLC và module

| # | Thiết bị | SL | Dùng cho | Bắt buộc |
|---|---|---|---|---|
| 1 | PLC **FX3U-32MR/ES** (16 vào / 16 ra relay) — *hoặc* giữ FX3U-16M và mua thêm mục 2 | 1 | Đủ X0–X12, Y0–Y13 | Có |
| 2 | Nếu giữ FX3U-16M: **FX2N-8EX** (8 vào) + **FX2N-8EYR** (8 ra relay) | 1+1 | X10–X12, Y10–Y13 | Có (nếu không đổi PLC) |
| 3 | **FX3U-CNV-BD** (board nối adapter bên trái) | 1 | Bắt buộc để lắp adapter ADP | Có |
| 4 | **FX3U-485ADP-MB** | 1 | Modbus RTU tới ESP32 | Có |
| 5 | **FX3U-4AD-ADP** (4 kênh analog 4–20 mA) | 1 | Đọc cảm biến nhiệt độ, độ ẩm → D50, D51 | Có |
| 6 | Cáp lập trình **USB-SC09-FX** | 1 | Nạp chương trình từ GX Works2 | Có (nếu chưa có) |

Thứ tự lắp bên trái PLC: CNV-BD → 485ADP-MB → 4AD-ADP (xem manual FX3U về thứ tự adapter).

## 2. Cảm biến

| # | Thiết bị | SL | Ngõ PLC | Ghi chú |
|---|---|---|---|---|
| 7 | Bộ chuyển đổi **nhiệt độ + độ ẩm ngõ ra 4–20 mA**, nguồn 24 VDC | 1 | 4AD-ADP kênh 1, 2 | Gửi mã cho Claude để viết phần đọc D50/D51 |
| 8 | Phao điện mức nước **thấp** | 1 | X3 (ON = nước thấp) | |
| 9 | Phao điện mức nước **cao** | 1 | X4 (ON = bồn đầy) | |
| 10 | Công tắc từ cửa (reed switch) | 1 | X5 (ON = cửa mở) | |
| 11 | Cảm biến mức thức ăn: **công tắc cánh quay (rotary paddle)** hoặc cảm biến điện dung 24 VDC | 3 | X10, X11, X12 (ON = còn thức ăn) | Gắn gần đáy thùng cá, vịt, gà. Chọn cùng kiểu NPN hoặc PNP, đấu chân S/S PLC cho đúng |

## 3. Thiết bị chấp hành

| # | Thiết bị | SL | Ngõ PLC | Ghi chú |
|---|---|---|---|---|
| 12 | Máy bơm nước | 1 | Y0 qua contactor | |
| 13 | Quạt thông gió | 2 | Y1, Y2 qua contactor | |
| 14 | Đèn chiếu sáng | theo chuồng | Y3 qua contactor/relay | |
| 15 | **Van điện từ nước thường đóng (NC)** 220 VAC hoặc 24 VDC | 1 | Y4 | Van nước xuống máng gà |
| 16 | **Máy cho cá ăn** (máy rải/thổi thức ăn) | 1 | Y10 qua contactor | |
| 17 | **Van xả thức ăn khô** cho thùng cá, vịt, gà: cửa trượt điện (slide gate) hoặc vít tải có motor | 3 | Y11, Y12, Y13 | Van điện từ nước KHÔNG dùng được cho cám viên |
| 18 | Còi báo 24 VDC hoặc 220 VAC | 1 | Y5 | |
| 19 | Đèn báo xanh (RUN) và đỏ (LỖI) | 1+1 | Y6, Y7 | |

## 4. Tủ điện

| # | Thiết bị | SL | Ghi chú |
|---|---|---|---|
| 20 | Tủ điện ngoài trời IP54 trở lên, có quạt hút | 1 | |
| 21 | Aptomat (MCB) tổng + MCB nhánh cho bơm, mỗi quạt, máy cá, đèn, nguồn điều khiển | ~7 | |
| 22 | **Contactor + rơ le nhiệt** cho bơm, quạt 1, quạt 2, máy cho cá ăn | 4 bộ | Chọn theo công suất motor |
| 23 | Contactor hoặc relay trung gian cho đèn và 3 van thức ăn | 4 | Ngõ ra relay PLC chỉ chịu ~2 A |
| 24 | Nguồn tổ ong / DIN **24 VDC 2.5–5 A** | 1 | Cảm biến, van 24 V, ESP32 |
| 25 | Nút **dừng khẩn nấm đỏ (NC)**, có khóa | 1 | X2 + **ngắt trực tiếp cuộn contactor** |
| 26 | Nút START xanh (NO), nút STOP đỏ (NC) | 1+1 | X0, X1 |
| 27 | Công tắc xoay 2 vị trí AUTO/MANUAL | 1 | X6 |
| 28 | **Công tắc chìa khóa** LOCAL/REMOTE | 1 | X7 |
| 29 | Chống sét lan truyền (SPD) nguồn 220 V | 1 | |
| 30 | Cầu đấu, thanh ray DIN, máng dây, dây điện, đầu cos, nhãn dây | — | |

## 5. Gateway ESP32

| # | Thiết bị | SL | Ghi chú |
|---|---|---|---|
| 31 | ESP32 DevKit (ESP32-WROOM-32) | 1 | Dự phòng thêm 1 |
| 32 | Module RS485 **3.3 V** (MAX3485, nên loại cách ly) | 1 | Code dùng chân DE/RE |
| 33 | Mạch hạ áp 24 V → 5 V | 1 | |
| 34 | Cáp xoắn đôi chống nhiễu + trở 120 Ω cuối đường dây | — | Nối 485ADP-MB ↔ ESP32 |
| 35 | Hộp nhựa nhỏ đặt trong tủ | 1 | |

## 6. Mạng và máy chủ

| # | Thiết bị | SL | Ghi chú |
|---|---|---|---|
| 36 | Router Wi-Fi (hoặc router 4G nếu trại không có mạng cáp) | 1 | Phủ sóng tới tủ điện |
| 37 | Máy chạy backend + MySQL + Mosquitto: **Mini PC** hoặc **Raspberry Pi 5 (4–8 GB) + SSD**, hoặc thuê VPS | 1 | |
| 38 | **UPS** cho PLC, router, máy chủ | 1 | Giữ báo cáo khi cúp điện |

## 7. Tùy chọn

| # | Thiết bị | Ghi chú |
|---|---|---|
| 39 | Màn hình HMI 7" (Weintek MT8071iE, Mitsubishi GOT…) | Dùng cho chế độ MANUAL (bit M30–M37, M56/M57, M240–M247). Không mua thì báo Claude đổi sang nút nhấn thật |
| 40 | Tiếp điểm phụ contactor đưa về PLC | Để biết thiết bị chạy thật (cần thêm ngõ vào) |
| 41 | Cảm biến CO₂, NH₃ ngõ ra 4–20 mA | D56, D57 — dùng 2 kênh còn lại của 4AD-ADP |
