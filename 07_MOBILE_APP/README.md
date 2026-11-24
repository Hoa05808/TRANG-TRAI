# FarmControl – App Flutter (Giai đoạn 5)

Flutter 3.50 · Dart 3.14 · Android / iOS / Web / Windows từ một mã nguồn.
Giao diện kiểu SCADA: nền xanh đen (hoặc sáng), xanh lá = đang chạy, xám = tắt, vàng = cảnh báo, đỏ = lỗi.

## 7 màn hình

| # | Màn hình | Nội dung | API |
|---|---|---|---|
| 01 | Tổng quan | Kết nối, chế độ, PLC, đồng hồ nhiệt độ/độ ẩm/bể nước, 5 thiết bị, cảnh báo | `/api/dashboard`, WebSocket `/topic/dashboard` |
| 02 | Điều khiển | Bơm, quạt, đèn, van: xác nhận trước khi gửi, hiện tiến trình lệnh, tự khóa khi mất kết nối / REMOTE bị khóa / tài khoản chỉ xem | `/api/commands`, `/topic/commands` |
| 03 | Cảm biến | 8 giá trị D50–D57 kèm chất lượng dữ liệu | `/api/dashboard` |
| 04 | Biểu đồ | Giờ / Ngày / Tuần / Tháng, đường trung bình + vùng min–max; dải thời gian chạy thiết bị | `/api/sensors/history`, `/api/history/device-runtime` |
| 05 | Cảnh báo | Đang diễn ra (xác nhận đã biết) và lịch sử | `/api/alerts/active`, `/api/alerts`, `/topic/alerts` |
| 06 | Lịch sử | Ai gửi lệnh gì và kết quả; thiết bị bật/tắt do app hay do PLC | `/api/history/commands`, `/api/history/device-events` |
| 07 | Cài đặt | Tài khoản, đổi mật khẩu, sáng/tối, ngưỡng cảnh báo, quản lý tài khoản & vai trò, bộ giả lập | `/api/settings`, `/api/users`, `/api/simulator/fault` |

Điện thoại: thanh dưới 5 mục, Lịch sử/Cài đặt ở góc trên. Màn hình rộng (≥ 900 px): thanh bên đủ 7 mục.

## Chạy

1. Chạy backend (`09_BACKEND/README.md`) và tạo CSDL bằng `08_DOCUMENTATION/FARM_AUTOMATION_mysql.sql`.
2. Chạy app:

```powershell
cd 07_MOBILE_APP\farmcontrol_app
C:\flutter\bin\flutter.bat pub get
C:\flutter\bin\flutter.bat run -d chrome
```

Địa chỉ máy chủ sửa ở màn hình đăng nhập:
- Web / Windows trên cùng máy: `http://localhost:8080`
- Máy ảo Android: `http://10.0.2.2:8080`
- Điện thoại thật: `http://<IP máy tính>:8080` (cùng Wi-Fi, mở cổng 8080 trên tường lửa Windows)

Android SDK đã cài ở `C:\Android\Sdk` (bản command-line, không có máy ảo). Build file cài cho điện thoại:

```powershell
C:\flutter\bin\flutter.bat build apk --release
```

File APK: `build\app\outputs\flutter-apk\app-release.apk`. Cách cài và nhận thông báo đẩy: `08_DOCUMENTATION/GIAI_DOAN_6_THONG_BAO.md`.
Build Windows cần bật *Developer Mode* của Windows.

## Cách app xử lý mất kết nối

- Dữ liệu chính đến qua WebSocket. Mất WebSocket thì tự hỏi REST mỗi 10 giây và tự kết nối lại sau 5 giây (lấy token mới).
- Backend báo `OFFLINE` / `PLC_LINK_LOST` thì thiết bị hiện **KHÔNG RÕ**, nút điều khiển bị khóa, có thanh báo đỏ/vàng.
- Lệnh không bao giờ được app tự gửi lại.
- Token lưu bằng `flutter_secure_storage`; access token tự làm mới, refresh token hết hạn thì quay về màn đăng nhập.

## Cấu trúc

```
lib/
  core/      models, api_client (HTTP + làm mới token), realtime (STOMP), session_store, farm_store
  theme/     scada_theme (bảng màu SCADA)
  widgets/   panel, đèn trạng thái, đồng hồ, danh sách phân trang
  screens/   7 màn hình + đăng nhập + khung điều hướng
```

## Còn lại

- Giai đoạn 6: nhận thông báo đẩy FCM (cần tạo dự án Firebase, thêm `firebase_messaging`, gọi `POST /api/push-tokens`).
- Thay biểu tượng app và màn hình khởi động.
