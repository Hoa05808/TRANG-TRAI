# Giai đoạn 6 – Thông báo cảnh báo (FCM + Telegram)

Mã nguồn đã sẵn sàng. Còn 3 việc **bạn tự làm bằng tài khoản của mình** (Claude không tạo tài khoản hay nhập token thay bạn).

| Kênh | Vai trò | Gửi khi nào |
|---|---|---|
| Thông báo đẩy vào app (Firebase Cloud Messaging) | Kênh chính | Mọi cảnh báo WARNING / CRITICAL, và khi cảnh báo kết thúc |
| Telegram | Dự phòng | Mặc định WARNING và CRITICAL (chỉ nhận khẩn cấp: `TELEGRAM_MIN_SEVERITY=CRITICAL`) |

Không dùng Zalo: cần Official Account doanh nghiệp và tin ZNS tính phí.

---

## A. Telegram (khoảng 10 phút)

1. Trong Telegram, tìm **@BotFather** → gửi `/newbot` → đặt tên (ví dụ `FarmControl Bot`) → nhận **token** dạng `123456:ABC...`.
2. Tạo một nhóm (ví dụ "Trang trại – Cảnh báo"), thêm bot và những người cần nhận cảnh báo.
3. Gửi một tin bất kỳ vào nhóm, rồi mở trên trình duyệt:
   `https://api.telegram.org/bot<TOKEN>/getUpdates`
   Tìm `"chat":{"id":-100...}` → đó là **chat id** (số âm với nhóm).
4. Đặt biến môi trường cho backend (KHÔNG ghi token vào file trong git):
   - **IntelliJ:** Run → Edit Configurations → FarmControlApplication → *Environment variables*, thêm
     `TELEGRAM_BOT_TOKEN=<token>;TELEGRAM_CHAT_ID=<chat id>`
   - Hoặc PowerShell trước khi chạy `mvnw`:
     ```powershell
     $env:TELEGRAM_BOT_TOKEN = "<token>"
     $env:TELEGRAM_CHAT_ID = "<chat id>"
     ```
5. Chạy lại backend. Log sẽ có dòng `Kênh thông báo: [Telegram (từ mức CRITICAL)]`.
   Thử: Cài đặt → Bộ giả lập → **Nhiệt độ cao** → nhóm Telegram nhận tin 🚨.

## B. Firebase – thông báo đẩy vào app (khoảng 20 phút)

1. Vào https://console.firebase.google.com → **Add project** → đặt tên `FarmControl` (có thể tắt Google Analytics).
2. **Thêm app Android:** biểu tượng Android → *Android package name* nhập chính xác:
   `vn.farmcontrol.farmcontrol_app`
   → tải file **google-services.json** → chép vào
   `07_MOBILE_APP\farmcontrol_app\android\app\google-services.json`
3. **Khóa cho backend:** Project settings → **Service accounts** → *Generate new private key* → tải file JSON.
   Lưu file **ngoài thư mục git**, ví dụ `C:\farm-secrets\firebase-admin.json`, rồi thêm biến môi trường cho backend:
   `FIREBASE_CREDENTIALS=C:\farm-secrets\firebase-admin.json`
   File này là khóa bí mật: không gửi qua chat, không commit lên GitHub.
4. Chạy lại backend → log có `Firebase Cloud Messaging` trong danh sách kênh.
5. Build lại APK (mục C), cài lên điện thoại, đăng nhập, cho phép thông báo.
   Cài đặt → *Thông báo đẩy* phải hiện "Đang nhận thông báo đẩy".

Điện thoại Xiaomi / Oppo / Vivo / Realme: vào cài đặt pin của app FarmControl, chọn **Không hạn chế** và bật **Tự khởi động**, nếu không máy có thể chặn thông báo khi app tắt.

## C. Cài app lên điện thoại Android

Cần Android SDK (cài Android Studio, hoặc để Claude cài bộ command-line tools). Sau đó:

```powershell
cd C:\Users\PC\Documents\GitHub\TRANG-TRAI\07_MOBILE_APP\farmcontrol_app
C:\flutter\bin\flutter.bat build apk --release
```

File: `build\app\outputs\flutter-apk\app-release.apk` → gửi sang điện thoại (cáp, Zalo, Drive) → mở để cài
(cho phép "cài ứng dụng không rõ nguồn gốc").

Trên màn đăng nhập của app, sửa **Máy chủ** thành `http://<IP máy tính>:8080`
(xem IP bằng lệnh `ipconfig`, dòng IPv4). Điện thoại và máy tính phải cùng Wi-Fi, và mở cổng 8080 trên tường lửa Windows:

```powershell
New-NetFirewallRule -DisplayName "FarmControl 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
```

(chạy PowerShell bằng quyền Administrator)

## D. iPhone

- Server chỉ chạy **backend**; app iPhone vẫn phải build riêng bằng **Mac + Xcode** (hoặc dịch vụ build trên mây như Codemagic)
  và cần **tài khoản Apple Developer (99 USD/năm)** để cài qua TestFlight / App Store. Thông báo đẩy iOS còn cần khóa APNs tải lên Firebase.
- Cách không cần Mac: đưa bản **web** của app lên server cùng backend (HTTPS). Trên iPhone mở Safari → Chia sẻ →
  **Thêm vào Màn hình chính**. Dùng được đầy đủ giám sát / điều khiển; thông báo đẩy web trên iOS 16.4+ cần làm thêm sau.
