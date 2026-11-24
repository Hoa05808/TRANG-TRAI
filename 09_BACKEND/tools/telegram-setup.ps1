# FarmControl - lấy chat id Telegram và gửi tin thử.
# Chạy:  powershell -ExecutionPolicy Bypass -File 09_BACKEND\tools\telegram-setup.ps1
# Token chỉ dùng trên máy này, không lưu vào file.

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

Write-Host ""
Write-Host "=== Kết nối Telegram cho FarmControl ===" -ForegroundColor Cyan
Write-Host "Trước khi chạy: mở Telegram, tìm bot của bạn và bấm START (hoặc gửi 1 tin bất kỳ cho bot)."
Write-Host ""

$secure = Read-Host "Dán token bot (từ @BotFather)" -AsSecureString
$token = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
if ([string]::IsNullOrWhiteSpace($token)) { Write-Host "Chưa nhập token." -ForegroundColor Red; exit 1 }
$api = "https://api.telegram.org/bot$($token.Trim())"

try {
    $me = Invoke-RestMethod "$api/getMe"
} catch {
    Write-Host "Token không đúng hoặc không có mạng: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
Write-Host "Bot: @$($me.result.username) - OK" -ForegroundColor Green

$updates = Invoke-RestMethod "$api/getUpdates"
$chats = @{}
foreach ($u in $updates.result) {
    $m = if ($u.message) { $u.message } elseif ($u.my_chat_member) { $u.my_chat_member } else { $null }
    if ($m -and $m.chat) {
        $name = if ($m.chat.title) { $m.chat.title } else { "$($m.chat.first_name) $($m.chat.last_name)".Trim() }
        $chats["$($m.chat.id)"] = "$name ($($m.chat.type))"
    }
}
if ($chats.Count -eq 0) {
    Write-Host "Chưa thấy tin nhắn nào gửi tới bot. Mở Telegram, bấm START với bot (hoặc gửi tin vào nhóm có bot) rồi chạy lại." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Các cuộc trò chuyện tìm thấy:"
$list = @($chats.GetEnumerator())
for ($i = 0; $i -lt $list.Count; $i++) { Write-Host "  [$i] chat id = $($list[$i].Key)  -  $($list[$i].Value)" }
$pick = 0
if ($list.Count -gt 1) { $pick = [int](Read-Host "Chọn số thứ tự") }
$chatId = $list[$pick].Key

$body = @{ chat_id = $chatId; text = "✅ FarmControl đã kết nối Telegram. Cảnh báo khẩn cấp của trang trại sẽ gửi vào đây." } | ConvertTo-Json
Invoke-RestMethod "$api/sendMessage" -Method Post -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
Write-Host ""
Write-Host "Đã gửi tin thử tới chat id $chatId - kiểm tra Telegram." -ForegroundColor Green
Write-Host ""
Write-Host "Bước tiếp theo - trong IntelliJ: Run > Edit Configurations > FarmControlApplication > Environment variables, thêm:" -ForegroundColor Cyan
Write-Host "  TELEGRAM_BOT_TOKEN=<token của bạn>;TELEGRAM_CHAT_ID=$chatId"
Write-Host "Rồi bấm Run lại backend."
