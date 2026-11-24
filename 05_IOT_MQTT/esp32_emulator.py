"""
ESP32 + PLC giả lập qua MQTT THẬT (Mosquitto) - để thử backend ở chế độ GATEWAY_MODE=mqtt khi chưa có phần cứng.

Làm đúng như firmware 04_GATEWAY_ESP32/src/main.cpp:
  - gửi telemetry d50/d100/d200 mỗi 2 giây lên farm/farm01/telemetry
  - Last Will "offline" + "online" (retained) trên farm/farm01/gateway/status
  - nhận lệnh, kiểm tra hạn exp, ghi D203/D204 rồi D210=reqId, "PLC" xử lý và ghi D205/D206, gửi ack
  - --plc-down: giả lập đứt RS485 -> gửi {"plcOk": false}

Chạy:  python 05_IOT_MQTT/esp32_emulator.py            (Ctrl+C để dừng)
       python 05_IOT_MQTT/esp32_emulator.py --plc-down
Cần:   pip install paho-mqtt   và đã chạy setup-mosquitto.ps1 (tạo local-credentials.json)
"""
import argparse
import json
import math
import random
import sys
import threading
import time
from pathlib import Path

import paho.mqtt.client as mqtt

FARM = "farm01"
GW = "esp32-01"
T_TELEMETRY = f"farm/{FARM}/telemetry"
T_COMMAND = f"farm/{FARM}/command"
T_ACK = f"farm/{FARM}/command/ack"
T_STATUS = f"farm/{FARM}/gateway/status"

# Lệnh D203 -> (thanh ghi trạng thái D100.., giá trị)
COMMANDS = {1: [(100, 1)], 2: [(100, 0)], 3: [(101, 1)], 4: [(101, 0), (102, 0)],
            5: [(103, 1)], 6: [(103, 0)], 7: [(104, 1)], 8: [(104, 0)]}


class VirtualPlc:
    """Một PLC FX3U tối giản: thanh ghi D, logic AUTO, xử lý lệnh theo bắt tay D210/D205."""

    def __init__(self):
        self.D = {i: 0 for i in range(0, 400)}
        self.D[103] = 1          # đèn bật
        self.D[105] = 1          # AUTO
        self.D[106] = 1          # RUN
        self.D[200] = 1          # phiên bản dữ liệu
        self.D[209] = 1          # cho phép REMOTE
        self.D[58] = self.D[59] = 1
        self.temp, self.level = 29.0, 55.0
        self.override_until = {}
        self.lock = threading.Lock()

    def scan(self):
        with self.lock:
            self.D[201] = (self.D[201] + 1) & 0x7FFF            # heartbeat PLC
            phase = (time.time() % 600) / 600 * 2 * math.pi
            fans = self.D[101] + self.D[102]
            self.temp += (31 + 4 * math.sin(phase) - self.temp) * 0.08 - fans * 0.1 + random.uniform(-0.05, 0.05)
            self.level = max(0, min(100, self.level + (0.9 if self.D[100] else 0) - 0.25))
            now = time.time()
            if self.override_until.get(100, 0) < now:            # AUTO bơm
                if self.level <= 30: self.D[100] = 1
                elif self.level >= 80: self.D[100] = 0
            if self.override_until.get(101, 0) < now:            # AUTO quạt
                if self.temp >= 30: self.D[101] = 1
                elif self.temp <= 28: self.D[101] = 0
            self.D[50] = round(self.temp * 10)
            self.D[51] = 720 + random.randint(-10, 10)
            self.D[52] = 285
            self.D[53] = round(self.level * 10)
            self.D[54], self.D[55], self.D[56], self.D[57] = 550, 1200, 800, 10
            self.D[109] = (self.D[109] + 2) & 0x7FFF
            # Bắt tay lệnh: D210 khác D205 => yêu cầu mới
            if self.D[210] != 0 and self.D[210] != self.D[205]:
                cmd = self.D[203]
                if self.D[209] != 1 or cmd not in COMMANDS:
                    self.D[206] = 2                               # từ chối
                else:
                    for reg, val in COMMANDS[cmd]:
                        self.D[reg] = val
                        self.override_until[reg] = now + 120
                    self.D[206] = 3                               # đã thực hiện
                self.D[205] = self.D[210]

    def read(self, start, n=10):
        with self.lock:
            return [self.D[start + i] for i in range(n)]

    def write(self, reg, value):
        with self.lock:
            self.D[reg] = value


def main():
    # Cửa sổ dòng lệnh Windows có thể dùng bảng mã không có tiếng Việt
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--plc-down", action="store_true", help="giả lập đứt RS485")
    ap.add_argument("--duration", type=float, default=0, help="tự dừng sau N giây (0 = chạy mãi)")
    args = ap.parse_args()

    cred = json.loads((Path(__file__).parent / "local-credentials.json").read_text(encoding="utf-8"))
    plc = VirtualPlc()
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=GW)
    client.username_pw_set(cred["username"], cred["password"])
    client.will_set(T_STATUS, "offline", qos=1, retain=True)
    busy = threading.Lock()

    def ack(req, cmd, result, msg=None):
        body = {"reqId": req, "cmd": cmd, "result": result}
        if msg: body["msg"] = msg
        client.publish(T_ACK, json.dumps(body), qos=1)
        print(f"[CMD] reqId={req} cmd={cmd} -> result={result} {msg or ''}")

    def handle_command(c):
        req, cmd, val, exp = c.get("reqId", 0), c.get("cmd", 0), c.get("val", 0), c.get("exp", 0)
        if time.time() > exp:
            return ack(req, cmd, 10, "Lệnh tới Gateway khi đã hết hạn")
        if not busy.acquire(blocking=False):
            return ack(req, cmd, 2, "Gateway đang xử lý lệnh khác")
        try:
            if args.plc_down:
                return ack(req, cmd, 11, "Modbus timeout - PLC không trả lời")
            plc.write(203, cmd); plc.write(204, val); plc.write(210, req)   # D210 ghi sau cùng
            deadline = time.time() + 8
            while time.time() < deadline:
                time.sleep(0.2)
                ack_req, result = plc.read(205, 2)
                if ack_req == req and result in (2, 3, 4):
                    ack(req, cmd, result)
                    plc.write(203, 0)
                    return
            ack(req, cmd, 11, "PLC không xác nhận lệnh trong 8 giây")
        finally:
            busy.release()

    connects = []

    def on_connect(cl, userdata, flags, rc, props):
        print(f"[MQTT] kết nối: {rc}", flush=True)
        now = time.time()
        connects[:] = [t for t in connects if now - t < 30] + [now]
        if len(connects) >= 3:
            print("[CẢNH BÁO] Bị ngắt và kết nối lại liên tục: có một ESP32 (hoặc giả lập) khác cùng tên "
                  f"'{GW}' đang chạy. Chỉ để chạy MỘT cửa sổ giả lập.", flush=True)
        if not rc.is_failure:
            cl.publish(T_STATUS, "online", qos=1, retain=True)
            cl.subscribe(T_COMMAND, qos=1)

    def on_message(cl, userdata, msg):
        try:
            threading.Thread(target=handle_command, args=(json.loads(msg.payload),), daemon=True).start()
        except ValueError:
            print("[CMD] JSON không hợp lệ")

    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(cred["host"], cred["port"], keepalive=30)
    client.loop_start()

    seq, started = 0, time.time()
    print("ESP32 giả lập đang chạy" + (" (ĐỨT RS485)" if args.plc_down else "") + ". Ctrl+C để dừng.")
    try:
        while not args.duration or time.time() - started < args.duration:
            plc.scan()
            if args.plc_down:
                client.publish(T_TELEMETRY, json.dumps({"gw": GW, "plcOk": False, "err": "Modbus timeout - PLC không trả lời"}))
            else:
                seq += 1
                client.publish(T_TELEMETRY, json.dumps(
                    {"gw": GW, "seq": seq, "d50": plc.read(50), "d100": plc.read(100), "d200": plc.read(200)}))
            time.sleep(2)
    except KeyboardInterrupt:
        pass
    client.publish(T_STATUS, "offline", qos=1, retain=True).wait_for_publish(2)
    client.loop_stop()
    client.disconnect()


if __name__ == "__main__":
    main()
