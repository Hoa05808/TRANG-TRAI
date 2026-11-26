// FarmControl Gateway - ESP32
//   PLC FX3U  <--RS485 / Modbus RTU-->  ESP32  <--Wi-Fi / MQTT-->  Mosquitto  <-->  Backend Spring Boot
//
// Nhiệm vụ:
//   1. Mỗi 2 giây đọc D50..D59, D100..D109, D200..D209 và gửi telemetry (giá trị thô).
//      Không đọc được PLC -> gửi {"plcOk":false} thay vì số liệu cũ.
//   2. Nhận lệnh từ backend, kiểm tra hạn (exp), ghi xuống PLC theo bắt tay reqId (D203, D204, rồi D210),
//      chờ PLC xác nhận (D205 = reqId, kết quả D206) rồi gửi phản hồi, sau đó xóa D203.
//   3. Không bao giờ tự lặp lại lệnh; chỉ một lệnh tại một thời điểm.
// PLC luôn là nơi quyết định cuối cùng (liên động an toàn). Mất Wi-Fi thì PLC vẫn chạy AUTO.

#include <Arduino.h>
#include <ArduinoJson.h>
#include <ModbusMaster.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <time.h>

#include "config.h"
#include "mqtt_secrets.h"

#if __has_include("wifi_secrets.h")
#include "wifi_secrets.h"
#else
#error "Thiếu include/wifi_secrets.h: chép include/wifi_secrets.example.h thành wifi_secrets.h và điền Wi-Fi"
#endif

// ---------- Topic ----------
static const String TOPIC_BASE = String("farm/") + FARM_ID;
static const String TOPIC_TELEMETRY = TOPIC_BASE + "/telemetry";
static const String TOPIC_COMMAND = TOPIC_BASE + "/command";
static const String TOPIC_ACK = TOPIC_BASE + "/command/ack";
static const String TOPIC_STATUS = TOPIC_BASE + "/gateway/status";

// Kết quả phản hồi (D206 và mã của Gateway)
enum AckResult : int { ACK_ACCEPTED = 1, ACK_REJECTED = 2, ACK_EXECUTED = 3, ACK_FAILED = 4, ACK_EXPIRED = 10, ACK_COMM_ERROR = 11 };

WiFiClient net;
PubSubClient mqtt(net);
ModbusMaster plc;

// ---------- Trạng thái ----------
uint32_t seq = 0;
uint16_t gatewayHeartbeat = 0;
uint8_t plcFailCount = 0;
uint8_t lastModbusError = 0;
unsigned long lastTelemetryAt = 0;
unsigned long lastMqttTryAt = 0;

struct Command {
    enum State { IDLE, PENDING, WAITING } state = IDLE;
    int reqId = 0;
    int cmd = 0;
    int val = 0;
    bool acceptedSent = false;
    unsigned long startedAt = 0;
    unsigned long lastPollAt = 0;
} command;

// ---------- RS485 ----------
void preTransmission() { digitalWrite(RS485_DE_RE_PIN, HIGH); }
void postTransmission() { digitalWrite(RS485_DE_RE_PIN, LOW); }

static uint16_t reg(uint16_t d) { return MODBUS_D_OFFSET + d; }

bool readBlock(uint16_t startD, int16_t* out) {
    uint8_t r = plc.readHoldingRegisters(reg(startD), BLOCK_SIZE);
    if (r != plc.ku8MBSuccess) {
        lastModbusError = r;
        return false;
    }
    for (int i = 0; i < BLOCK_SIZE; i++) out[i] = (int16_t)plc.getResponseBuffer(i);
    return true;
}

bool writeRegister(uint16_t d, uint16_t value) {
    uint8_t r = plc.writeSingleRegister(reg(d), value);
    if (r != plc.ku8MBSuccess) lastModbusError = r;
    return r == plc.ku8MBSuccess;
}

String modbusErrorText(uint8_t code) {
    switch (code) {
        case 0xE2: return "Modbus timeout - PLC không trả lời";
        case 0xE3: return "Modbus sai CRC - kiểm tra dây A/B, tốc độ, parity";
        case 0xE0: return "Modbus sai slave id";
        case 0x02: return "Modbus sai địa chỉ thanh ghi";
        default: return "Modbus lỗi 0x" + String(code, HEX);
    }
}

// ---------- MQTT ----------
void publishJson(const String& topic, JsonDocument& doc) {
    char buf[512];
    size_t n = serializeJson(doc, buf, sizeof(buf));
    mqtt.publish(topic.c_str(), (const uint8_t*)buf, n, false);
}

void sendAck(int reqId, int cmd, int result, const char* msg = nullptr) {
    JsonDocument doc;
    doc["reqId"] = reqId;
    doc["cmd"] = cmd;
    doc["result"] = result;
    if (msg) doc["msg"] = msg;
    publishJson(TOPIC_ACK, doc);
    Serial.printf("[CMD] reqId=%d cmd=%d -> result=%d %s\n", reqId, cmd, result, msg ? msg : "");
}

bool timeSynced() { return time(nullptr) > 1700000000; }  // sau 11/2023 = đã có giờ NTP

void onMqttMessage(char* topic, byte* payload, unsigned int len) {
    if (TOPIC_COMMAND != topic) return;
    JsonDocument doc;
    if (deserializeJson(doc, payload, len)) {
        Serial.println("[CMD] JSON lệnh không hợp lệ, bỏ qua");
        return;
    }
    int reqId = doc["reqId"] | 0;
    int cmd = doc["cmd"] | 0;
    int val = doc["val"] | 0;
    long long exp = doc["exp"] | 0LL;

    if (reqId < 1 || reqId > 32767 || cmd < 1 || cmd > 8) {
        sendAck(reqId, cmd, ACK_REJECTED, "Lệnh không hợp lệ");
        return;
    }
    if (!timeSynced()) {
        // Không biết giờ thì không kiểm tra được hạn lệnh -> từ chối để an toàn
        sendAck(reqId, cmd, ACK_EXPIRED, "Gateway chưa đồng bộ giờ NTP");
        return;
    }
    if ((long long)time(nullptr) > exp) {
        sendAck(reqId, cmd, ACK_EXPIRED, "Lệnh tới Gateway khi đã hết hạn");
        return;
    }
    if (command.state != Command::IDLE) {
        sendAck(reqId, cmd, ACK_REJECTED, "Gateway đang xử lý lệnh khác");
        return;
    }
    command.reqId = reqId;
    command.cmd = cmd;
    command.val = val;
    command.acceptedSent = false;
    command.state = Command::PENDING;  // ghi Modbus trong loop(), không ghi trong callback MQTT
}

void ensureMqtt() {
    if (mqtt.connected() || WiFi.status() != WL_CONNECTED) return;
    if (millis() - lastMqttTryAt < MQTT_RETRY_MS && lastMqttTryAt != 0) return;
    lastMqttTryAt = millis();
    Serial.printf("[MQTT] Kết nối %s:%d ...\n", MQTT_HOST, MQTT_PORT);
    // Last Will: broker tự gửi "offline" (retained) khi Gateway mất kết nối đột ngột
    if (mqtt.connect(GATEWAY_ID, MQTT_USER, MQTT_PASS, TOPIC_STATUS.c_str(), 1, true, "offline")) {
        mqtt.publish(TOPIC_STATUS.c_str(), "online", true);
        mqtt.subscribe(TOPIC_COMMAND.c_str(), 1);
        Serial.println("[MQTT] Đã kết nối, đã đăng ký nhận lệnh");
    } else {
        Serial.printf("[MQTT] Lỗi kết nối, mã %d (4/5 = sai tài khoản)\n", mqtt.state());
    }
}

// ---------- Telemetry ----------
void readAndPublishTelemetry() {
    int16_t d50[BLOCK_SIZE], d100[BLOCK_SIZE], d200[BLOCK_SIZE];
    bool ok = readBlock(REG_SENSORS, d50) && readBlock(REG_STATUS, d100) && readBlock(REG_COMM, d200);

    if (!ok) {
        plcFailCount++;
        Serial.printf("[PLC] Đọc lỗi (%u lần): %s\n", plcFailCount, modbusErrorText(lastModbusError).c_str());
        if (plcFailCount >= PLC_FAIL_BEFORE_REPORT && mqtt.connected()) {
            JsonDocument doc;
            doc["gw"] = GATEWAY_ID;
            doc["plcOk"] = false;
            doc["err"] = modbusErrorText(lastModbusError);
            publishJson(TOPIC_TELEMETRY, doc);
        }
        return;
    }
    plcFailCount = 0;
    writeRegister(REG_GW_HEARTBEAT, ++gatewayHeartbeat & 0x7FFF);

    if (!mqtt.connected()) return;
    JsonDocument doc;
    doc["gw"] = GATEWAY_ID;
    doc["seq"] = ++seq;
    JsonArray a = doc["d50"].to<JsonArray>();
    JsonArray b = doc["d100"].to<JsonArray>();
    JsonArray c = doc["d200"].to<JsonArray>();
    for (int i = 0; i < BLOCK_SIZE; i++) {
        a.add(d50[i]);
        b.add(d100[i]);
        c.add(d200[i]);
    }
    publishJson(TOPIC_TELEMETRY, doc);
}

// ---------- Lệnh: bắt tay reqId với PLC ----------
void processCommand() {
    if (command.state == Command::PENDING) {
        // reqId trùng D205 (ví dụ CSDL backend bị tạo lại, reqId đếm lại từ 1): PLC sẽ coi là lệnh cũ
        // và ESP32 sẽ đọc nhầm kết quả cũ trong D206 -> từ chối, không ghi xuống PLC.
        uint8_t r = plc.readHoldingRegisters(reg(REG_ACK_REQ), 1);
        if (r != plc.ku8MBSuccess) {
            lastModbusError = r;
            sendAck(command.reqId, command.cmd, ACK_COMM_ERROR, modbusErrorText(lastModbusError).c_str());
            command.state = Command::IDLE;
            return;
        }
        if ((int)plc.getResponseBuffer(0) == command.reqId) {
            sendAck(command.reqId, command.cmd, ACK_REJECTED, "reqId trùng lệnh PLC vừa xử lý (D205), gửi lại lệnh mới");
            command.state = Command::IDLE;
            return;
        }
        // Ghi D203 (mã lệnh), D204 (giá trị) TRƯỚC, D210 (reqId) SAU CÙNG: PLC thấy D210 đổi mới xử lý
        plc.setTransmitBuffer(0, command.cmd);
        plc.setTransmitBuffer(1, command.val);
        bool ok = plc.writeMultipleRegisters(reg(REG_CMD_ID), 2) == plc.ku8MBSuccess;
        ok = ok && writeRegister(REG_REQ_ID, command.reqId);
        if (!ok) {
            sendAck(command.reqId, command.cmd, ACK_COMM_ERROR, modbusErrorText(lastModbusError).c_str());
            command.state = Command::IDLE;
            return;
        }
        command.startedAt = command.lastPollAt = millis();
        command.state = Command::WAITING;
        return;
    }
    if (command.state != Command::WAITING || millis() - command.lastPollAt < CMD_POLL_INTERVAL_MS) return;
    command.lastPollAt = millis();

    if (plc.readHoldingRegisters(reg(REG_ACK_REQ), 2) == plc.ku8MBSuccess) {
        int ackReq = plc.getResponseBuffer(0);
        int result = plc.getResponseBuffer(1);
        if (ackReq == command.reqId) {
            if (result == ACK_ACCEPTED && !command.acceptedSent) {
                sendAck(command.reqId, command.cmd, ACK_ACCEPTED);
                command.acceptedSent = true;
            } else if (result == ACK_REJECTED || result == ACK_EXECUTED || result == ACK_FAILED) {
                sendAck(command.reqId, command.cmd, result);
                writeRegister(REG_CMD_ID, 0);  // xóa ô lệnh
                command.state = Command::IDLE;
                return;
            }
        }
    }
    if (millis() - command.startedAt > CMD_PLC_TIMEOUT_MS) {
        sendAck(command.reqId, command.cmd, ACK_COMM_ERROR, "PLC không xác nhận lệnh trong 8 giây");
        writeRegister(REG_CMD_ID, 0);
        command.state = Command::IDLE;
    }
}

// ---------- Chương trình chính ----------
void setup() {
    Serial.begin(115200);
    Serial.println("\nFarmControl Gateway " GATEWAY_ID);

    pinMode(RS485_DE_RE_PIN, OUTPUT);
    postTransmission();
    Serial2.begin(MODBUS_BAUD, MODBUS_CONFIG, RS485_RX_PIN, RS485_TX_PIN);
    plc.begin(MODBUS_SLAVE_ID, Serial2);
    plc.preTransmission(preTransmission);
    plc.postTransmission(postTransmission);

    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    configTime(0, 0, "pool.ntp.org", "time.google.com");  // giờ UTC để kiểm tra hạn lệnh

    mqtt.setServer(MQTT_HOST, MQTT_PORT);
    mqtt.setBufferSize(1024);
    mqtt.setKeepAlive(30);
    mqtt.setCallback(onMqttMessage);
}

void loop() {
    ensureMqtt();
    mqtt.loop();
    processCommand();
    if (millis() - lastTelemetryAt >= TELEMETRY_INTERVAL_MS && command.state != Command::WAITING) {
        lastTelemetryAt = millis();
        readAndPublishTelemetry();
    }
}
