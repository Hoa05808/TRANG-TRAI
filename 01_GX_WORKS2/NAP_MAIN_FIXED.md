# Nạp chương trình MAIN_FIXED vào GX Works2

| File | Vai trò |
|---|---|
| `MAIN_FIXED_IL.txt` | Mã nguồn IL có tiêu đề từng đoạn (sửa ở đây) |
| `MAIN_FIXED.csv` | File cho GX Works2 đọc (sinh từ file trên), có tiêu đề từng đoạn |
| `MAIN_FIXED_KHONG_TIEU_DE.csv` | Cùng chương trình, bỏ tiêu đề đoạn. Dùng nếu bản trên vẫn báo lỗi Line Statement |
| `COMMENT.csv` | Comment bạn xuất từ GX Works2 (bản gốc, giữ làm sao lưu) |
| `COMMENT_FIXED.csv` | Comment cũ giữ nguyên + 82 comment mới (gồm phần cho ăn). Nạp: Global Device Comment → Edit → Read from CSV File |
| `tools/merge_comments.py` | Script gộp comment mới vào bản xuất, không đổi comment cũ |
| `tools/il_to_gxcsv.py` | Script sinh CSV, tự tính số step (đã đối chiếu khớp 100% với `MAIN.csv` gốc) |
| `REVIEW_MAIN_2026-11-26.md` | Danh sách lỗi của bản cũ và lý do sửa |

Sinh lại CSV sau khi sửa IL:
```powershell
cd C:\Users\Admin\Documents\GitHub\TRANG-TRAI\01_GX_WORKS2
python -X utf8 tools\il_to_gxcsv.py MAIN_FIXED_IL.txt MAIN_FIXED.csv
```

## 1. Sao lưu trước
**Project → Save As** → `TRANG TRAI_BAN_CU.gxw`. Sau đó mở lại `TRANG TRAI.gxw` để sửa.

## 2. Đọc CSV
1. Mở chương trình **MAIN** (cây Project → Program → MAIN).
2. **Edit → Read from CSV File** → chọn `MAIN_FIXED.csv` → đồng ý ghi đè.
3. Nhấn **F4 (Convert)**.
4. **Tool → Check Program**: không được có lỗi; cảnh báo "double coil" chỉ chấp nhận với `M43/M45`… nếu còn sót (bản mới đã bỏ trùng cuộn).

Nếu GX Works2 báo lỗi ở một dòng, ghi lại số step và nội dung, gửi cho Claude.

## 3. Thông số PLC
**Parameter → PLC Parameter → Device**: đặt vùng giữ khi mất điện (Latch) của **D bắt đầu từ D0** (ví dụ D0–D511).
Nếu không đặt được, chương trình vẫn tự nạp giá trị mẫu cho D0–D9 khi chúng bằng 0.

Cấu hình Modbus kênh 1 (adapter FX3U-485ADP-MB): xem mục 4 trong `LADDER_D210_HANDSHAKE.md`.

## 4. Phần cứng / HMI cần biết
- **X7 = công tắc khóa REMOTE** (mới). Chưa lắp thì REMOTE không bật, app chỉ xem, không điều khiển được — đúng ý an toàn.
- **X5 = cảm biến cửa**: chương trình hiểu **ON = cửa mở**. Nếu cảm biến của bạn ngược lại, đổi `LD X005` thành `LDI X005` ở đoạn 20B.
- Bit HMI: M30/M31 bơm, M32–M35 quạt, M56/M57 đèn, **M37 tắt còi**.

## 5. Quyết định đã chốt (26/11/2026 — muốn đổi thì sửa ở `MAIN_FIXED_IL.txt`)
| Nội dung | Đã chốt | Sửa ở đoạn |
|---|---|---|
| REMOTE có tắt AUTO không | Có (giữ như bản cũ) | 04 (`ANI M180` trong M10) |
| Bơm có cần nhấn START (M1) | Không (giữ như bản cũ) | 10 (thêm `AND M1` vào M28) |
| Giờ tắt đèn mỗi mùa | 05:25 cho cả 4 mùa | 19 |
| Dừng khẩn có tắt đèn | Không (giữ như bản cũ) | 20 |

## 6. Thử trên GX Simulator
Làm đủ 10 bước ở mục **E** trong `REVIEW_MAIN_2026-11-26.md`, thêm:
11. X3 = ON, chế độ MANUAL, bơm tắt → sau 5 s M101 = ON, còi kêu, D108 = 5. Bật M37 → còi tắt.
12. AUTO, bơm chạy liên tục > D4 giây (đặt D4 = 10 để thử nhanh) → Y0 tắt, M27 = ON, D108 = 3. Nhấn X0 → xóa.
13. X5 = ON 5 s → M107, D108 = 6 (còi không kêu, chỉ báo lên app).

### Thử phần cho ăn (bật X10, X11, X12 = ON trước = thùng còn thức ăn)
Mẹo: ghi D400 = giờ PLC hiện tại + 1 phút (xem D82), D410 = 10, D411 = 5 để thử nhanh.
14. Đến giờ D400 → Y10 bật, 3 s sau Y11 mở, 10 s sau Y11 đóng, 5 s sau Y10 tắt. D115 tăng 1.
15. Bật M242 → Y12 mở D412 giây rồi tự đóng. Bật M245 khi Y13 đang mở → Y13 đóng ngay.
16. Đang cho cá ăn, tắt X2 (dừng khẩn) → Y10, Y11 tắt ngay, không tự chạy lại khi nhả.
17. Tắt X11 30 s → M221 = ON, D114 = 2, đèn Y7 sáng, còi KHÔNG kêu.
18. REMOTE: D203 = 13, D210 = mã mới → Y13 mở, D206 = 3. Khi M1 = OFF gửi lại → D206 = 2.
19. Van nước máng gà: đặt D414 = 10. Đến giờ D404 → Y13 (thức ăn gà) và Y4 (nước) cùng mở; Y4 đóng sau 10 s.
    Bật M246 → Y4 mở, bật M247 → Y4 đóng ngay. REMOTE: D203 = 7 → Y4 mở, D206 = 3; D203 = 8 → Y4 đóng.

### Phần cứng cho ăn
Cần thêm 3 ngõ vào (X10–X12) và 4 ngõ ra (Y10–Y13). FX3U-16M không đủ: lắp module mở rộng
(ví dụ FX2N-8EX + FX2N-8EYR) rồi khai báo trong **Parameter → I/O Assignment** nếu cần. Chi tiết: `02_IO_LIST/IO.txt`.
D400–D413 nằm trong vùng giữ mặc định nên giờ và thời gian cho ăn không mất khi cúp điện.
