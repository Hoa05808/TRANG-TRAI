# Nhận xét chương trình MAIN (xuất ngày 26/11/2026, 783 step)

Nguồn: `MAIN.csv` xuất từ GX Works2. Số step ghi trong ngoặc, ví dụ (479), là số step trong GX Works2.
**Chưa sửa gì trong file `.gxw`.** Các đoạn code dưới đây là đề xuất. Bạn tự nhập vào, chạy thử bằng GX Simulator rồi mới nạp xuống PLC.

## Điểm làm tốt
- STOP (X1) và dừng khẩn (X2) dùng logic thường đóng: tín hiệu ON nghĩa là bình thường, nên đứt dây cũng được coi là dừng. Đúng hướng an toàn.
- M6 kiểm tra cảm biến mực nước mâu thuẫn (thấp và cao cùng ON), M2 báo lỗi.
- Quạt dùng `OUT` cuối cùng (Y1, Y2), có trễ bật/tắt (hysteresis) M51 bằng D61/D62.
- Lệnh quạt từ xa M55 có tự giữ, bị xóa khi rời REMOTE hoặc khi hệ thống dừng.
- Có heartbeat D201 (LDP M8013 → INC), D200 = phiên bản, M8002 bỏ qua lệnh cũ.

## A. Lỗi nghiêm trọng (phải sửa trước khi nạp PLC thật)

| # | Step | Lỗi | Hậu quả |
|---|---|---|---|
| A1 | 124–140 | Rung `RST Y000` kết thúc bằng `ANI M24`, nên **khi có lệnh dừng thì không reset được bơm** | Nhấn **dừng khẩn hoặc STOP khi bơm đang chạy (AUTO/MANUAL), bơm vẫn chạy** |
| A2 | 85–87, 134–140 | Lệnh bơm từ xa M22 chỉ ON 1 chu kỳ (M170 là xung), chu kỳ sau nhánh 3 reset Y0 ngay | Bật bơm từ app: bơm chỉ chạy 1 chu kỳ quét rồi tắt |
| A3 | 670–676 | `LD M182` + `ANI M182` luôn sai, nên **không bao giờ ghi D205 = D210** | ESP32 không nhận được xác nhận, mọi lệnh báo hết thời gian sau 8 giây. M181 kẹt ON, M183 kẹt (chặn mãi) |
| A4 | cả khối | Không có lệnh nào ghi **D206 = 3** (đã thực hiện) | App không bao giờ thấy "Đã thực hiện" |
| A5 | — | **M180 không được bật ở đâu** và **D209 không được ghi ở đâu** | Không bao giờ vào REMOTE (M12), mọi lệnh từ app bị từ chối |
| A6 | 473–484 | D100: `LD Y000 → MOV K1`, rồi `LD Y000 → MOV K0` (phải là `LDI`) | D100 luôn = 0, app luôn thấy bơm TẮT dù đang chạy |
| A7 | 734–740 | Lệnh 6 (TẮT ĐÈN) giải mã ra **M176** (mở van) thay vì **M175** | Tắt đèn từ app không có tác dụng |
| A8 | 539–568 | D106 gửi 0/1/2/3/4 (chạy = 2), backend hiểu 0 Stop / 1 Run / **2 Error** | Hệ thống đang chạy thì app báo **PLC LỖI** và chặn lệnh |
| A9 | 295–433 | Giờ/tháng so sánh bằng **hằng số H (hex)**. Thanh ghi đồng hồ D8014/D8015/D8017 của FX3U là số **nhị phân**: H18 = 24, H30 = 48, H10 = 16… | Lịch đèn sai: tháng 10, 11, 12 không thuộc mùa nào. Giờ 18:xx không bao giờ khớp |
| A10 | 183–188 | D65 (nhiệt độ hợp lệ) không được ghi ở đâu, nên **D60 không bao giờ nhận D50** | Quạt AUTO theo nhiệt độ không chạy |
| A11 | — | D0–D9 và D61/D62 nằm trong vùng **không giữ khi mất điện** (FX3U mặc định chỉ giữ D200–D511) | Mất điện xong ngưỡng = 0: D60 ≥ 0 luôn đúng, quạt chạy liên tục |

## B. Lỗi logic / cần dọn

| # | Step | Nội dung |
|---|---|---|
| B1 | 42–46 | Còi Y5 = `M9 OR (NOT M1) OR M48`: **còi kêu suốt khi hệ thống chưa chạy**. Còi chỉ nên kêu khi có lỗi hoặc cảnh báo, và nên có nút tắt còi |
| B2 | 153–182 | Khối quạt AUTO cũ so sánh **D100 ≥ D101** (trạng thái bơm với quạt, sai thanh ghi). Khối này OUT trùng (**double coil**) M43, M44, M45 với step 254–261, và còn ghi nhầm **M66 (lỗi đèn)**. → **Xóa toàn bộ 153–182** |
| B3 | 266–270 | M48 "lỗi quạt" = có lệnh mà Y1/Y2 chưa ON, nên nháy 1 chu kỳ mỗi lần bật quạt và làm còi kêu. Lỗi quạt thật cần tiếp điểm phụ contactor đưa về ngõ vào, cộng thêm timer |
| B4 | 141–152, 109–123 | Bơm AUTO/MANUAL **không cần M1** (chưa nhấn START vẫn chạy), còn quạt và đèn cần M1. Nên thống nhất (xem mục D) |
| B5 | — | Bơm tay hoặc từ xa không bị chặn khi **bồn đã đầy (X4)** → nguy cơ tràn |
| B6 | 448–472 | Đèn dùng SET/RST Y3 có điều kiện M67 = M0·M1, nên khi STOP hoặc dừng khẩn **đèn giữ nguyên trạng thái** |
| B7 | 521–538 | D105 không về 0 khi không ở chế độ nào. REMOTE gửi 3, nhưng backend chưa biết mã 3 |
| B8 | — | Van Y4 chưa có logic, D104 không được ghi. Lệnh 7/8 đang bị từ chối, như vậy là đúng tạm thời |
| B9 | 213, 763–781 | Các bit thừa: M54 (không dùng), M184/M185/M186 (M185 luôn = 0). Có thể xóa |
| B10 | 220–237 | Chế độ tay: M32 **hoặc** M34 đều bật **cả hai** quạt, chưa điều khiển riêng quạt 1, quạt 2 được |
| B11 | — | Chưa có: timer bơm quá giờ (D4, M27), trễ cảnh báo (D5), cảnh báo cửa X5, cảnh báo M100–M109, đọc cảm biến analog vào D50–D57, theo dõi Gateway (D202 → M105) |

## C. Đoạn code đề xuất (IL – FX3U)

### C1. Còi (thay step 42–46)
```
LD     M9          ; lỗi hoặc dừng khẩn
OUT    Y005        ; còi  (sau này thêm ANI <bit tắt còi>)
LD     M9
OUT    Y007        ; đèn lỗi
```

### C2. Bơm: bỏ SET/RST Y0, dùng một cuộn OUT (thay step 85–87, 92–97 và 109–140)
```
; Lệnh dừng: thêm bồn đầy X4
LDI    X001
ORI    X002
OR     M171
OR     M2
OR     M5
OR     X004        ; bồn đầy -> dừng ở mọi chế độ
OR     M27         ; bơm chạy quá giờ
OUT    M24

; Tự giữ lệnh bơm từ xa (M36 là bit trống)
LD     M12
AND    M170
SET    M36
LD     M171
ORI    M12
OR     M24
RST    M36
LD     M12
AND    M36
OUT    M22

; Ngõ ra bơm
LD     M20
AND    M10
LD     M21
AND    M11
ORB
LD     M22
AND    M12
ORB
AND    M28
AND    M29
ANI    M24
OUT    Y000
```
Lưu ý: `M29 = M6 · /M2 · /M24` đã có `/M24`, nên lệnh bật bơm từ xa khi bồn đầy sẽ bị PLC từ chối (D206 = 2).

### C3. Bơm quá giờ D4 (giây), timer 100 ms
```
LD     M8000
MUL    D4    K10   D70     ; D70 = D4 x 10
LD     Y000
OUT    T0    D70
LD     T0
SET    M27                 ; PUMP_TIMEOUT
SET    M102                ; ALARM_PUMP_TIMEOUT
LD     X000                ; nhấn START để xóa
RST    M27
RST    M102
```

### C4. Nhiệt độ hợp lệ và ngưỡng quạt (thêm trước step 183, xóa 153–182)
```
LD     M8000
MOV    K0    D65
LD=    D58   K1            ; cảm biến nhiệt báo OK
MOV    K1    D65
LD     M8000
MOV    D0    D61           ; ngưỡng bật = D0
MOV    D1    D62           ; ngưỡng tắt = D1
```

### C5. Giá trị mặc định khi mất điện (đặt đầu chương trình)
Cách tốt nhất: **Parameter → PLC Parameter → Device**, đặt vùng giữ (latch) cho D bắt đầu từ D0.
Dù đã đặt latch, vẫn nên nạp giá trị mặc định khi thông số bằng 0:
```
LD     M8002
AND=   D0    K0
MOV    K300  D0
LD     M8002
AND=   D1    K0
MOV    K280  D1
LD     M8002
AND=   D4    K0
MOV    K300  D4
```
(làm tương tự cho D2, D3, D5–D9)

### C6. Lịch đèn: đổi H thành K (thay step 295–433)
```
; Mùa theo tháng (D8017 = 1..12)
LD=    D8017 K2
OR=    D8017 K3
OR=    D8017 K4
OUT    M70
LD=    D8017 K5
OR=    D8017 K6
OR=    D8017 K7
OUT    M71
LD=    D8017 K8
OR=    D8017 K9
OR=    D8017 K10
OUT    M72
LD=    D8017 K11
OR=    D8017 K12
OR=    D8017 K1
OUT    M73

; Ví dụ mùa M70: bật 18:30 -> tắt 05:25 (qua nửa đêm)
LD     M70
LD=    D8015 K18
AND>=  D8014 K30
OR>    D8015 K18
LD=    D8015 K5
AND<   D8014 K25
ORB
OR<    D8015 K5
ANB
OUT    M74
```
**Bạn cần xác nhận:** M75/M76/M77 hiện chỉ có giờ bật (18:50, 18:30, 17:45) mà không có giờ tắt, nên đèn chỉ sáng tới hết giờ đó. Mỗi mùa bật và tắt lúc mấy giờ? Khi đã có giờ, nên đưa giờ bật/tắt vào thanh ghi D (ví dụ D9) để chỉnh từ HMI thay vì sửa chương trình.

### C7. Trạng thái gửi lên app (thay step 473–600)
```
LD     M8000
MOV    K0    D100
MOV    K0    D101
MOV    K0    D102
MOV    K0    D103
MOV    K0    D104
LD     Y000
MOV    K1    D100
LD     Y001
MOV    K1    D101
LD     Y002
MOV    K1    D102
LD     Y003
MOV    K1    D103
LD     Y004
MOV    K1    D104

; D105: 0 Stop, 1 Auto, 2 Manual, 3 Remote
LD     M8000
MOV    K0    D105
LD     M10
MOV    K1    D105
LD     M11
MOV    K2    D105
LD     M12
MOV    K3    D105      ; backend cần thêm mã 3 = REMOTE

; D106: 0 Stop, 1 Run, 2 Error  (khớp backend)
LD     M8000
MOV    K0    D106
LD     M1
MOV    K1    D106
LD     M9
MOV    K2    D106
```
(D107/D108 giữ như cũ.)

### C8. Theo dõi Gateway và cho phép REMOTE
```
; Công tắc LOCAL/REMOTE: cần chọn nguồn cho M180 (xem mục D)
LD     X007            ; ví dụ: công tắc khóa REMOTE trên tủ
OUT    M180

; D202 đứng yên 30 s -> mất Gateway
LD=    D202  D211
OUT    T2    K300
LD<>   D202  D211
MOV    D202  D211
LD     T2
OUT    M105            ; ALARM_GATEWAY_OFFLINE

; D209 = được phép điều khiển từ xa
LD     M8000
MOV    K0    D209
LD     M12
ANI    M105
ANI    M3
MOV    K1    D209
```

### C9. Bộ xử lý lệnh từ xa: viết lại (thay toàn bộ step 610–782)
```
LD     M8002
MOV    K1    D200
MOV    D210  D205          ; bỏ qua lệnh cũ còn trong thanh ghi
MOV    K0    D206
MOV    K0    D203

; Có yêu cầu mới
LD<>   D210  D205
AND<>  D210  K0
OUT    M181

; Hợp lệ
LD     M181
AND=   D209  K1
ANI    M3
AND>=  D203  K1
AND<=  D203  K8
OUT    M182

; Bị chặn: lệnh BẬT không qua liên động; van (7, 8) chưa lắp -> từ chối
LD     M182
LD=    D203  K1
ANI    M29
LD=    D203  K3
ANI    M49
ORB
LD=    D203  K5
ANI    M67
ORB
OR=    D203  K7
OR=    D203  K8
ANB
OUT    M183

; Chấp nhận (chỉ 1 chu kỳ, vì D205 được cập nhật ngay cuối khối)
LD     M182
ANI    M183
OUT    M188

LD     M188
AND=   D203  K1
OUT    M170
LD     M188
AND=   D203  K2
OUT    M171
LD     M188
AND=   D203  K3
OUT    M172
LD     M188
AND=   D203  K4
OUT    M173
LD     M188
AND=   D203  K5
OUT    M174
LD     M188
AND=   D203  K6
OUT    M175                ; (trước đây nhầm M176)

; Kết quả
LD     M181
ANI    M182
MOV    K2    D206          ; không hợp lệ / không cho phép REMOTE
LD     M182
AND    M183
MOV    K2    D206          ; bị liên động chặn
LD     M188
MOV    K3    D206          ; đã thực hiện
LD     M188
OUT    M178                ; REMOTE_COMMAND_ACCEPTED
LD     M181
ANI    M188
OUT    M179                ; REMOTE_COMMAND_REJECTED

; Xác nhận cho ESP32 - LUÔN đặt cuối khối
LD     M181
MOV    D210  D205
```
Thứ tự quét vẫn đúng: M170…M175 được OUT ở cuối chương trình, và **chu kỳ quét kế tiếp** khối bơm/quạt/đèn ở đầu chương trình đọc được, rồi bit tự về 0.

## D. Các quyết định thuộc về bạn
1. **M180 lấy từ đâu?** Công tắc khóa LOCAL/REMOTE trên tủ (ví dụ X7, khuyên dùng), nút trên HMI, hay cả hai?
2. **REMOTE có tắt AUTO không?** Hiện tại vào REMOTE thì M10 = 0, nghĩa là quạt không tự bật theo nhiệt độ và bơm không tự cấp nước. Phổ biến hơn là: AUTO vẫn chạy, app chỉ bật/tắt thêm.
3. **Bơm có cần nhấn START (M1) không?** Hiện quạt và đèn cần M1, còn bơm thì không.
4. **Giờ tắt đèn của từng mùa** (mục C6).
5. **Dừng khẩn có tắt đèn không?**

## E. Thử trên GX Simulator (sau khi sửa)
1. RUN, X1 = X2 = ON, nhấn X0 → M1 = ON, Y6 sáng, còi Y5 tắt.
2. AUTO (X6 = ON), X3 = ON → Y0 ON. Thả X2 (dừng khẩn) → **Y0 phải TẮT ngay** (lỗi A1).
3. X4 = ON → Y0 tắt. X3 = X4 = ON → M2, còi kêu.
4. M180 = ON → D209 = 1. Ghi D203 = 1, rồi D210 = 1 → Y0 ON và **giữ**, D205 = 1, D206 = 3.
5. Ghi D203 = 1, D210 = 2 → xử lý lại (gửi lại cùng mã lệnh vẫn nhận).
6. Ghi D203 = 6, D210 = 3 → Y3 tắt.
7. Ghi D203 = 7, D210 = 4 → D206 = 2 (van chưa lắp).
8. Giữ D202 không đổi 30 s → M105 = ON, D209 = 0.
9. Đặt D50 = 320, D58 = 1, D0 = 300, D1 = 280, AUTO → Y1/Y2 ON. D50 = 270 → tắt.
10. Tắt nguồn rồi bật lại (STOP → RUN) → D0–D9 vẫn còn giá trị.
