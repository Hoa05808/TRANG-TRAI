package vn.farmcontrol.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.farmcontrol.alert.AlertEvaluator;
import vn.farmcontrol.command.CommandService;
import vn.farmcontrol.command.DeviceCommand;
import vn.farmcontrol.config.FarmProperties;
import vn.farmcontrol.gateway.GatewayEvents.BrokerConnectionChanged;
import vn.farmcontrol.gateway.GatewayEvents.GatewayStatusReported;
import vn.farmcontrol.gateway.GatewayEvents.TelemetryReceived;
import vn.farmcontrol.history.DeviceEvent;
import vn.farmcontrol.history.DeviceEventRepository;
import vn.farmcontrol.realtime.RealtimePublisher;
import vn.farmcontrol.state.DashboardService;
import vn.farmcontrol.state.FarmSnapshot;
import vn.farmcontrol.state.FarmStateService;
import vn.farmcontrol.state.FarmStateService.ConnectionStatus;
import vn.farmcontrol.state.FarmStateService.DeviceChange;

/** Xử lý dữ liệu đi lên từ Gateway: cập nhật trạng thái, ghi lịch sử, đánh giá cảnh báo, đẩy realtime. */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final FarmStateService state;
    private final SensorReadingRepository readings;
    private final DeviceEventRepository deviceEvents;
    private final CommandService commands;
    private final AlertEvaluator alertEvaluator;
    private final DashboardService dashboard;
    private final RealtimePublisher realtime;
    private final Duration storeInterval;

    private volatile Instant lastStoredAt;
    private volatile ConnectionStatus lastPublishedStatus;

    public TelemetryService(FarmStateService state, SensorReadingRepository readings,
                            DeviceEventRepository deviceEvents, CommandService commands,
                            AlertEvaluator alertEvaluator, DashboardService dashboard,
                            RealtimePublisher realtime, FarmProperties props) {
        this.state = state;
        this.readings = readings;
        this.deviceEvents = deviceEvents;
        this.commands = commands;
        this.alertEvaluator = alertEvaluator;
        this.dashboard = dashboard;
        this.realtime = realtime;
        this.storeInterval = Duration.ofSeconds(props.telemetry().storeIntervalSeconds());
    }

    @EventListener
    public synchronized void onTelemetry(TelemetryReceived event) {
        FarmSnapshot snap = FarmSnapshot.from(event.message(), event.receivedAt());
        List<DeviceChange> changes = state.update(snap);

        for (DeviceChange c : changes) {
            DeviceCommand cause = commands.findLikelyCause(c.device(), c.turnedOn(), c.at()).orElse(null);
            deviceEvents.save(new DeviceEvent(c.device(), c.turnedOn(),
                    cause != null ? DeviceEvent.Source.REMOTE : DeviceEvent.Source.PLC,
                    cause != null ? cause.getId() : null,
                    cause != null ? cause.getRequestedByName() : null,
                    c.at()));
            log.info("{} -> {} ({})", c.device(), c.turnedOn() ? "BẬT" : "TẮT",
                    cause != null ? "lệnh từ xa của " + cause.getRequestedByName() : "PLC");
        }

        if (lastStoredAt == null || Duration.between(lastStoredAt, snap.receivedAt()).compareTo(storeInterval) >= 0) {
            readings.save(new SensorReading(snap.receivedAt(), snap.sensors()));
            lastStoredAt = snap.receivedAt();
        }

        alertEvaluator.evaluate(snap);
        commands.confirmFromSnapshot(snap);
        if (lastPublishedStatus != null && lastPublishedStatus != ConnectionStatus.ONLINE) {
            alertEvaluator.checkConnectivity(); // kết nối vừa phục hồi -> hết cảnh báo ngay
        }
        publishDashboard();
    }

    @EventListener
    public void onGatewayStatus(GatewayStatusReported event) {
        log.info("Gateway báo trạng thái: {}", event.online() ? "online" : "offline");
        state.setGatewayReportedOnline(event.online());
        if (!event.online()) {
            alertEvaluator.checkConnectivity();
        }
        publishDashboard();
    }

    @EventListener
    public synchronized void onPlcLinkDown(vn.farmcontrol.gateway.GatewayEvents.PlcLinkDownReported event) {
        boolean wasDown = state.connectionStatus() == ConnectionStatus.PLC_LINK_LOST;
        state.reportPlcLinkDown(event.error(), event.receivedAt());
        if (!wasDown) {
            log.warn("Gateway báo không đọc được PLC qua RS485: {}", event.error());
            alertEvaluator.checkConnectivity(); // cảnh báo ngay, không chờ heartbeat
            publishDashboard();
        }
    }

    @EventListener
    public void onBrokerConnection(BrokerConnectionChanged event) {
        state.setBrokerConnected(event.connected());
    }

    /** Khi mất kết nối thì không có telemetry -> cần chủ động đẩy trạng thái OFFLINE cho app. */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void publishOnConnectionChange() {
        if (state.connectionStatus() != lastPublishedStatus) {
            publishDashboard();
        }
    }

    private void publishDashboard() {
        var d = dashboard.build();
        lastPublishedStatus = d.connection();
        realtime.dashboard(d);
    }
}
