package vn.farmcontrol.state;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.farmcontrol.plc.Device;
import vn.farmcontrol.setting.SettingKey;
import vn.farmcontrol.setting.SettingsService;

/**
 * Giữ trạng thái mới nhất trong bộ nhớ và đánh giá kết nối.
 * Nguyên tắc: khi dữ liệu cũ (mất kết nối) thì KHÔNG khẳng định thiết bị đang chạy hay tắt -> UNKNOWN.
 */
@Service
public class FarmStateService {

    public enum ConnectionStatus {
        /** Nhận telemetry đều và heartbeat PLC đang thay đổi. */
        ONLINE,
        /** Gateway còn gửi dữ liệu nhưng heartbeat PLC đứng yên (đứt RS485 / PLC treo). */
        PLC_LINK_LOST,
        /** Không nhận được telemetry từ Gateway. */
        OFFLINE
    }

    public record DeviceChange(Device device, boolean turnedOn, Instant at) {
    }

    private final SettingsService settings;
    private final Instant startedAt = Instant.now();

    private volatile FarmSnapshot latest;
    private volatile Instant lastHeartbeatChangeAt;
    private volatile boolean gatewayReportedOnline;
    private volatile boolean brokerConnected;
    private final Map<Device, Instant> onSince = new EnumMap<>(Device.class);
    private volatile Double waterLevelAtPumpStart;
    private volatile Instant lastGatewayContactAt;
    private volatile boolean plcLinkDown;
    private volatile String plcLinkError;

    public FarmStateService(SettingsService settings) {
        this.settings = settings;
    }

    /** Cập nhật ảnh chụp mới, trả về danh sách thiết bị đổi trạng thái so với lần trước. */
    public synchronized List<DeviceChange> update(FarmSnapshot snap) {
        FarmSnapshot prev = latest;
        List<DeviceChange> changes = new ArrayList<>();
        for (Device d : Device.values()) {
            boolean now = snap.isOn(d);
            if (prev == null) {
                if (now) onSince.put(d, snap.receivedAt());
                continue;
            }
            if (prev.isOn(d) != now) {
                changes.add(new DeviceChange(d, now, snap.receivedAt()));
                if (now) onSince.put(d, snap.receivedAt());
                else onSince.remove(d);
                if (d == Device.PUMP_1) {
                    waterLevelAtPumpStart = now ? snap.sensors().waterLevel() : null;
                }
            }
        }
        if (prev == null && snap.isOn(Device.PUMP_1)) {
            waterLevelAtPumpStart = snap.sensors().waterLevel();
        }
        if (prev == null || prev.plcHeartbeat() != snap.plcHeartbeat() || lastHeartbeatChangeAt == null) {
            lastHeartbeatChangeAt = snap.receivedAt();
        }
        latest = snap;
        gatewayReportedOnline = true;
        lastGatewayContactAt = snap.receivedAt();
        plcLinkDown = false;
        plcLinkError = null;
        return changes;
    }

    /** ESP32 vẫn kết nối nhưng báo không đọc được PLC qua RS485 (gói {"plcOk":false}). */
    public void reportPlcLinkDown(String error, Instant at) {
        lastGatewayContactAt = at;
        gatewayReportedOnline = true;
        plcLinkDown = true;
        plcLinkError = error;
    }

    public String plcLinkError() {
        return plcLinkError;
    }

    public FarmSnapshot latest() {
        return latest;
    }

    public boolean isDataFresh() {
        FarmSnapshot s = latest;
        return s != null && Duration.between(s.receivedAt(), Instant.now()).toSeconds()
                < settings.getInt(SettingKey.GATEWAY_OFFLINE_SECONDS);
    }

    public ConnectionStatus connectionStatus() {
        // Gateway tự báo offline (Last Will) thì coi là mất kết nối ngay, không chờ hết thời gian
        int offlineSec = settings.getInt(SettingKey.GATEWAY_OFFLINE_SECONDS);
        Instant contact = lastGatewayContactAt;
        boolean gatewayFresh = contact != null && Duration.between(contact, Instant.now()).toSeconds() < offlineSec;
        if (!gatewayReportedOnline || !gatewayFresh) return ConnectionStatus.OFFLINE;
        // Gateway còn liên lạc nhưng báo lỗi RS485, hoặc không còn gửi được dữ liệu PLC
        if (plcLinkDown || !isDataFresh()) return ConnectionStatus.PLC_LINK_LOST;
        Instant hb = lastHeartbeatChangeAt;
        if (hb != null && Duration.between(hb, Instant.now()).toSeconds()
                >= settings.getInt(SettingKey.PLC_HEARTBEAT_TIMEOUT_SECONDS)) {
            return ConnectionStatus.PLC_LINK_LOST;
        }
        return ConnectionStatus.ONLINE;
    }

    /** Trạng thái thiết bị dùng để hiển thị: null = không xác định (mất kết nối). */
    public Boolean deviceStateOrUnknown(Device d) {
        if (connectionStatus() != ConnectionStatus.ONLINE) return null;
        return latest.isOn(d);
    }

    public synchronized Instant onSince(Device d) {
        return onSince.get(d);
    }

    public Double waterLevelAtPumpStart() {
        return waterLevelAtPumpStart;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public void setGatewayReportedOnline(boolean online) {
        this.gatewayReportedOnline = online;
    }

    public boolean gatewayReportedOnline() {
        return gatewayReportedOnline;
    }

    public void setBrokerConnected(boolean connected) {
        this.brokerConnected = connected;
    }

    public boolean brokerConnected() {
        return brokerConnected;
    }
}
