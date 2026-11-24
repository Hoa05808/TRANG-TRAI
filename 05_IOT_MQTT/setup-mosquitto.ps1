# FarmControl - cấu hình Mosquitto trên Windows (chạy bằng quyền Administrator).
#
# Script này:
#   1. Tạo mật khẩu ngẫu nhiên cho 2 tài khoản MQTT: "backend" và "esp32-01"
#   2. Ghi cấu hình broker: bắt buộc đăng nhập + ACL, nghe cổng 1883 (mạng LAN)
#   3. Mở cổng 1883 trên tường lửa Windows cho mạng Private (để ESP32 qua Wi-Fi kết nối được)
#   4. Ghi tài khoản vào các file cục bộ đã nằm trong .gitignore:
#        09_BACKEND/farmcontrol-backend/local-mqtt.yml (backend đọc tự động)
#        04_GATEWAY_ESP32/include/mqtt_secrets.h       (firmware ESP32)
#        05_IOT_MQTT/local-credentials.json            (ESP32 giả lập bằng Python)
#   5. Khởi động lại dịch vụ mosquitto
# Mật khẩu KHÔNG được in ra màn hình. Chạy lại script = tạo mật khẩu mới cho cả 3 file.

param([string]$LanIp = "")
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Hãy chạy script bằng quyền Administrator." -ForegroundColor Red
    exit 1
}

$repo = Split-Path -Parent $PSScriptRoot
$mqDir = "C:\Program Files\mosquitto"
$dataDir = "C:\ProgramData\mosquitto-farm"
if (-not (Test-Path "$mqDir\mosquitto.exe")) { throw "Chưa cài Mosquitto tại $mqDir" }
New-Item -ItemType Directory -Force -Path $dataDir, "$dataDir\data" | Out-Null

function New-Password {
    $chars = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'.ToCharArray()
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}
$backendPass = New-Password
$espPass = New-Password

# 1. File mật khẩu (băm bằng mosquitto_passwd)
$passwd = "$dataDir\passwd"
if (Test-Path $passwd) { Remove-Item $passwd -Force }
New-Item -ItemType File -Path $passwd | Out-Null
& "$mqDir\mosquitto_passwd.exe" -b $passwd backend $backendPass
& "$mqDir\mosquitto_passwd.exe" -b $passwd esp32-01 $espPass

# 2. ACL + cấu hình
Copy-Item "$PSScriptRoot\acl.conf" "$dataDir\acl.conf" -Force
$conf = @"
# FarmControl - sinh bởi 05_IOT_MQTT/setup-mosquitto.ps1
per_listener_settings false
allow_anonymous false
password_file C:/ProgramData/mosquitto-farm/passwd
acl_file C:/ProgramData/mosquitto-farm/acl.conf

listener 1883

persistence true
persistence_location C:/ProgramData/mosquitto-farm/data/
log_dest file C:/ProgramData/mosquitto-farm/mosquitto.log
log_type error
log_type warning
log_type notice
log_type information
"@
if (-not (Test-Path "$mqDir\mosquitto.conf.original")) {
    Copy-Item "$mqDir\mosquitto.conf" "$mqDir\mosquitto.conf.original" -ErrorAction SilentlyContinue
}
Set-Content -Path "$mqDir\mosquitto.conf" -Value $conf -Encoding ascii

# 3. Tường lửa: chỉ mạng Private
if (-not (Get-NetFirewallRule -DisplayName "FarmControl MQTT 1883" -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName "FarmControl MQTT 1883" -Direction Inbound -Protocol TCP -LocalPort 1883 `
        -Profile Private -Action Allow | Out-Null
}

# 4. Ghi tài khoản vào file cục bộ (UTF-8 không BOM)
if (-not $LanIp) {
    $LanIp = (Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias "Wi-Fi" -ErrorAction SilentlyContinue |
              Select-Object -First 1).IPAddress
}
$utf8 = New-Object System.Text.UTF8Encoding($false)

$localYml = @"
# Sinh bởi 05_IOT_MQTT/setup-mosquitto.ps1 - KHÔNG commit (đã có trong .gitignore)
farm:
  mqtt:
    url: tcp://localhost:1883
    username: backend
    password: "$backendPass"
"@
[IO.File]::WriteAllText("$repo\09_BACKEND\farmcontrol-backend\local-mqtt.yml", $localYml, $utf8)

New-Item -ItemType Directory -Force -Path "$repo\04_GATEWAY_ESP32\include" | Out-Null
$secretsH = @"
// Sinh bởi 05_IOT_MQTT/setup-mosquitto.ps1 - KHÔNG commit (đã có trong .gitignore)
#pragma once
#define MQTT_HOST "$LanIp"
#define MQTT_PORT 1883
#define MQTT_USER "esp32-01"
#define MQTT_PASS "$espPass"
"@
[IO.File]::WriteAllText("$repo\04_GATEWAY_ESP32\include\mqtt_secrets.h", $secretsH, $utf8)

$credJson = @"
{ "host": "localhost", "port": 1883, "username": "esp32-01", "password": "$espPass" }
"@
[IO.File]::WriteAllText("$PSScriptRoot\local-credentials.json", $credJson, $utf8)

# 5. Khởi động lại dịch vụ
Restart-Service mosquitto
Start-Sleep -Seconds 1
$svc = Get-Service mosquitto
Write-Host ""
Write-Host "Mosquitto: $($svc.Status) - cổng 1883, bắt buộc đăng nhập" -ForegroundColor Green
Write-Host "IP máy tính cho ESP32: $LanIp"
Write-Host "Đã ghi tài khoản vào: local-mqtt.yml (backend), mqtt_secrets.h (ESP32), local-credentials.json (giả lập)"
