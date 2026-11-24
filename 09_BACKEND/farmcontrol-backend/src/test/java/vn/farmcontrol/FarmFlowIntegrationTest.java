package vn.farmcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.farmcontrol.gateway.GatewayClient;
import vn.farmcontrol.gateway.GatewayClient.CommandMessage;
import vn.farmcontrol.gateway.GatewayEvents.CommandAckReceived;
import vn.farmcontrol.gateway.GatewayEvents.TelemetryReceived;
import vn.farmcontrol.plc.TelemetryMessage;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FarmFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationEventPublisher events;
    @MockitoBean GatewayClient gateway;

    private int heartbeat;

    @BeforeEach
    void setUp() {
        when(gateway.isBrokerConnected()).thenReturn(true);
        when(gateway.mode()).thenReturn("test");
    }

    // ---------- helpers ----------

    private String login(String user, String pass) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, pass)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }

    /** Gửi telemetry giả: nhiệt độ (°C), mực nước (%), bơm bật/tắt. PLC RUN, AUTO, cho phép REMOTE. */
    private void telemetry(double temp, double level, boolean pumpOn) {
        int[] d50 = {(int) (temp * 10), 700, 280, (int) (level * 10), 500, 1000, 700, 5, 1, 1};
        int[] d100 = {pumpOn ? 1 : 0, 0, 0, 1, 0, 1, 1, 0, 0, 100};
        int[] d200 = {1, ++heartbeat, 1, 0, 0, 0, 0, 0, 0, 1};
        events.publishEvent(new TelemetryReceived(new TelemetryMessage("test-gw", (long) heartbeat, d50, d100, d200),
                Instant.now()));
    }

    private JsonNode getJson(String url, String token) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private org.springframework.test.web.servlet.ResultActions sendCommand(String token, String type) throws Exception {
        return mvc.perform(post("/api/commands").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"" + type + "\"}"));
    }

    // ---------- tests ----------

    @Test
    void authAndRoles() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        String owner = login("owner", "owner-pass-123");
        mvc.perform(get("/api/me").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("OWNER"));

        mvc.perform(post("/api/users").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"viewer1\",\"password\":\"viewer-pass-1\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated());
        String viewer = login("viewer1", "viewer-pass-1");

        // VIEWER được xem nhưng không được điều khiển hay quản lý tài khoản
        mvc.perform(get("/api/dashboard").header("Authorization", viewer)).andExpect(status().isOk());
        sendCommand(viewer, "PUMP_ON").andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", viewer)).andExpect(status().isForbidden());

        // Refresh token xoay vòng: dùng lại token cũ bị từ chối
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"owner-pass-123\"}"))
                .andReturn().getResponse().getContentAsString();
        String refresh = json.readTree(body).get("refreshToken").asString();
        String req = "{\"refreshToken\":\"" + refresh + "\"}";
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void commandLifecycle() throws Exception {
        String owner = login("owner", "owner-pass-123");

        // Chưa có dữ liệu từ trang trại -> không cho gửi lệnh, và ghi nhận lệnh BLOCKED
        sendCommand(owner, "PUMP_ON").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GATEWAY_OFFLINE"));
        verify(gateway, never()).publishCommand(any());

        telemetry(29.0, 50.0, false);
        JsonNode dash = getJson("/api/dashboard", owner);
        assertThat(dash.get("connection").asString()).isEqualTo("ONLINE");
        assertThat(dash.get("sensors").get("coopTemp").asDouble()).isEqualTo(29.0);
        assertThat(dash.get("devices").get(0).get("state").asString()).isEqualTo("OFF");

        // Gửi lệnh bật bơm
        String body = sendCommand(owner, "PUMP_ON").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andReturn().getResponse().getContentAsString();
        long cmdId = json.readTree(body).get("id").asLong();
        ArgumentCaptor<CommandMessage> sent = ArgumentCaptor.forClass(CommandMessage.class);
        verify(gateway).publishCommand(sent.capture());
        CommandMessage msg = sent.getValue();
        assertThat(msg.cmd()).isEqualTo(1);
        assertThat(msg.reqId()).isBetween(1, 32767);
        assertThat(msg.exp()).isGreaterThan(Instant.now().getEpochSecond());

        // Chỉ một lệnh tại một thời điểm
        sendCommand(owner, "FAN_ON").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_IN_PROGRESS"));

        // Phản hồi sai mã lệnh bị bỏ qua
        events.publishEvent(new CommandAckReceived(msg.reqId(), 5, 3, null, Instant.now()));
        assertThat(getJson("/api/commands/" + cmdId, owner).get("status").asString()).isEqualTo("SENT");

        events.publishEvent(new CommandAckReceived(msg.reqId(), 1, 1, null, Instant.now()));
        assertThat(getJson("/api/commands/" + cmdId, owner).get("status").asString()).isEqualTo("ACCEPTED");
        events.publishEvent(new CommandAckReceived(msg.reqId(), 1, 3, null, Instant.now()));
        JsonNode cmd = getJson("/api/commands/" + cmdId, owner);
        assertThat(cmd.get("status").asString()).isEqualTo("EXECUTED");
        assertThat(cmd.get("confirmedAt").isNull()).isTrue();

        // Telemetry cho thấy bơm thật sự bật -> xác nhận lệnh + lịch sử ghi đúng người điều khiển
        telemetry(29.0, 50.0, true);
        assertThat(getJson("/api/commands/" + cmdId, owner).get("confirmedAt").isNull()).isFalse();
        JsonNode ev = getJson("/api/history/device-events", owner).get("items").get(0);
        assertThat(ev.get("device").asString()).isEqualTo("PUMP_1");
        assertThat(ev.get("source").asString()).isEqualTo("REMOTE");
        assertThat(ev.get("username").asString()).isEqualTo("owner");

        // Gửi lại CÙNG mã lệnh vẫn được (reqId mới) - khắc phục vấn đề D203/D210 trên PLC
        sendCommand(owner, "PUMP_ON").andExpect(status().isAccepted());
        verify(gateway, times(2)).publishCommand(sent.capture());
        assertThat(sent.getValue().reqId()).isNotEqualTo(msg.reqId());

        JsonNode history = getJson("/api/history/commands", owner);
        assertThat(history.get("totalItems").asInt()).isEqualTo(3); // BLOCKED + EXECUTED + SENT
    }

    @Test
    void alertsRaiseAndResolve() throws Exception {
        String owner = login("owner", "owner-pass-123");
        telemetry(38.0, 50.0, false);
        JsonNode active = getJson("/api/alerts/active", owner);
        assertThat(active.size()).isEqualTo(1);
        assertThat(active.get(0).get("type").asString()).isEqualTo("TEMP_HIGH");
        assertThat(active.get(0).get("severity").asString()).isEqualTo("CRITICAL");

        // Không tạo cảnh báo trùng khi điều kiện vẫn còn
        telemetry(38.5, 50.0, false);
        assertThat(getJson("/api/alerts/active", owner).size()).isEqualTo(1);

        long id = active.get(0).get("id").asLong();
        mvc.perform(post("/api/alerts/" + id + "/ack").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acknowledgedBy").value("owner"));

        // Trong vùng trễ (34.5°C) vẫn còn cảnh báo; xuống 33°C thì hết
        telemetry(34.5, 50.0, false);
        assertThat(getJson("/api/alerts/active", owner).size()).isEqualTo(1);
        telemetry(33.0, 50.0, false);
        assertThat(getJson("/api/alerts/active", owner).size()).isZero();

        // Mực nước thấp
        telemetry(30.0, 10.0, false);
        assertThat(getJson("/api/alerts/active", owner).get(0).get("type").asString()).isEqualTo("WATER_LOW");
    }

    @Test
    void settingsValidation() throws Exception {
        String owner = login("owner", "owner-pass-123");
        mvc.perform(put("/api/settings").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alert.temp.high\": 99}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SETTING_OUT_OF_RANGE"));
        mvc.perform(put("/api/settings").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alert.temp.high\": 32}"))
                .andExpect(status().isOk());
        telemetry(32.5, 50.0, false);
        assertThat(getJson("/api/alerts/active", owner).get(0).get("type").asString()).isEqualTo("TEMP_HIGH");
    }

    @Test
    void gatewayReportsPlcLinkDownImmediately() throws Exception {
        String owner = login("owner", "owner-pass-123");
        telemetry(29.0, 50.0, false);
        assertThat(getJson("/api/dashboard", owner).get("connection").asString()).isEqualTo("ONLINE");

        // ESP32 không đọc được PLC -> báo ngay, không chờ heartbeat 30 giây
        events.publishEvent(new vn.farmcontrol.gateway.GatewayEvents.PlcLinkDownReported(
                "test-gw", "Modbus timeout", Instant.now()));
        JsonNode dash = getJson("/api/dashboard", owner);
        assertThat(dash.get("connection").asString()).isEqualTo("PLC_LINK_LOST");
        assertThat(dash.get("devices").get(0).get("state").asString()).isEqualTo("UNKNOWN");
        JsonNode active = getJson("/api/alerts/active", owner);
        assertThat(active.get(0).get("type").asString()).isEqualTo("PLC_LINK_LOST");
        assertThat(active.get(0).get("message").asString()).contains("Modbus timeout");
        sendCommand(owner, "PUMP_ON").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLC_LINK_LOST"));

        // Đọc được PLC trở lại -> ONLINE và hết cảnh báo
        telemetry(29.0, 50.0, false);
        assertThat(getJson("/api/dashboard", owner).get("connection").asString()).isEqualTo("ONLINE");
        assertThat(getJson("/api/alerts/active", owner).size()).isZero();
    }
}
