package vn.farmcontrol.command;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.farmcontrol.auth.CurrentUser;
import vn.farmcontrol.command.CommandDtos.CommandResponse;
import vn.farmcontrol.common.ApiException;
import vn.farmcontrol.config.FarmProperties;
import vn.farmcontrol.gateway.GatewayClient;
import vn.farmcontrol.gateway.GatewayClient.CommandMessage;
import vn.farmcontrol.gateway.GatewayEvents.CommandAckReceived;
import vn.farmcontrol.plc.CommandType;
import vn.farmcontrol.plc.Device;
import vn.farmcontrol.plc.PlcEnums.PlcState;
import vn.farmcontrol.realtime.RealtimePublisher;
import vn.farmcontrol.state.FarmSnapshot;
import vn.farmcontrol.state.FarmStateService;
import vn.farmcontrol.state.FarmStateService.ConnectionStatus;

/**
 * Quản lý lệnh điều khiển từ xa.
 *
 * Quy tắc an toàn:
 *  - Chỉ một lệnh được xử lý tại một thời điểm (PLC chỉ có một ô lệnh D203).
 *  - Mỗi lệnh có reqId riêng => gửi lại CÙNG một mã lệnh (ví dụ bật bơm 2 lần) vẫn được PLC nhận.
 *  - Lệnh có hạn (exp). Không phản hồi => TIMEOUT, KHÔNG tự động gửi lại khi kết nối phục hồi.
 *  - Lệnh chỉ là YÊU CẦU; PLC quyết định có thực hiện hay không theo liên động an toàn.
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    private final DeviceCommandRepository repo;
    private final GatewayClient gateway;
    private final FarmStateService state;
    private final RealtimePublisher realtime;
    private final Duration ttl;
    private final Duration ackTimeout;
    private final Duration confirmWindow;
    private final Object lock = new Object();

    public CommandService(DeviceCommandRepository repo, GatewayClient gateway, FarmStateService state,
                          RealtimePublisher realtime, FarmProperties props) {
        this.repo = repo;
        this.gateway = gateway;
        this.state = state;
        this.realtime = realtime;
        this.ttl = Duration.ofSeconds(props.gateway().commandTtlSeconds());
        this.ackTimeout = Duration.ofSeconds(props.gateway().commandAckTimeoutSeconds());
        this.confirmWindow = Duration.ofSeconds(props.gateway().commandConfirmWindowSeconds());
    }

    public CommandResponse submit(CommandType type, CurrentUser user) {
        DeviceCommand cmd;
        synchronized (lock) {
            cmd = new DeviceCommand(type, user.id(), user.username(), Instant.now().plus(ttl));
            Optional<String[]> blocked = precheck();
            if (blocked.isPresent()) {
                String[] reason = blocked.get();
                cmd = repo.save(cmd);
                cmd.assignRequestId();
                cmd.finish(CommandStatus.BLOCKED, null, reason[1], Instant.now());
                repo.save(cmd);
                realtime.commandUpdated(CommandResponse.of(cmd));
                throw new ApiException(HttpStatus.CONFLICT, reason[0], reason[1]);
            }
            if (repo.existsByStatusIn(CommandStatus.IN_FLIGHT)) {
                throw ApiException.conflict("COMMAND_IN_PROGRESS",
                        "Đang có lệnh khác chờ PLC xử lý, vui lòng đợi vài giây");
            }
            cmd = repo.save(cmd);
            cmd.assignRequestId();
            cmd.markSent();
            cmd = repo.save(cmd); // lưu SENT trước khi gửi để phản hồi đến sớm vẫn tìm thấy lệnh
        }

        try {
            gateway.publishCommand(new CommandMessage(cmd.getRequestId(), cmd.getPlcCode(), cmd.getValue(),
                    cmd.getExpiresAt().getEpochSecond(), cmd.getCreatedAt().getEpochSecond()));
            log.info("Gửi lệnh #{} {} (reqId={}) bởi {}", cmd.getId(), type, cmd.getRequestId(), user.username());
        } catch (GatewayClient.GatewayUnavailableException e) {
            synchronized (lock) {
                cmd = repo.findById(cmd.getId()).orElseThrow();
                cmd.finish(CommandStatus.FAILED, null, e.getMessage(), Instant.now());
                cmd = repo.save(cmd);
            }
            realtime.commandUpdated(CommandResponse.of(cmd));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GATEWAY_UNAVAILABLE", e.getMessage());
        }
        CommandResponse response = CommandResponse.of(repo.findById(cmd.getId()).orElse(cmd));
        realtime.commandUpdated(response);
        return response;
    }

    /** @return [mã lỗi, thông báo] nếu không được phép gửi lệnh */
    private Optional<String[]> precheck() {
        ConnectionStatus conn = state.connectionStatus();
        if (conn == ConnectionStatus.OFFLINE) {
            return Optional.of(new String[]{"GATEWAY_OFFLINE",
                    "Mất kết nối với trang trại, không thể gửi lệnh. PLC vẫn chạy tự động."});
        }
        if (conn == ConnectionStatus.PLC_LINK_LOST) {
            return Optional.of(new String[]{"PLC_LINK_LOST",
                    "Gateway không giao tiếp được với PLC, không thể gửi lệnh."});
        }
        if (!gateway.isBrokerConnected()) {
            return Optional.of(new String[]{"BROKER_DISCONNECTED", "Máy chủ chưa kết nối được MQTT broker."});
        }
        FarmSnapshot snap = state.latest();
        if (snap.plcState() != PlcState.RUN) {
            return Optional.of(new String[]{"PLC_NOT_RUNNING", "PLC không ở trạng thái RUN."});
        }
        if (!snap.remoteEnabled()) {
            return Optional.of(new String[]{"REMOTE_DISABLED",
                    "PLC đang không cho phép điều khiển từ xa (chuyển công tắc sang chế độ REMOTE)."});
        }
        return Optional.empty();
    }

    @EventListener
    public void onAck(CommandAckReceived ack) {
        DeviceCommand cmd;
        synchronized (lock) {
            Optional<DeviceCommand> found = repo.findFirstByRequestIdAndCreatedAtAfterOrderByIdDesc(
                    ack.reqId(), ack.receivedAt().minus(Duration.ofMinutes(10)));
            if (found.isEmpty()) {
                log.warn("Phản hồi lệnh không khớp lệnh nào: reqId={} cmd={} result={}", ack.reqId(), ack.cmd(), ack.result());
                return;
            }
            cmd = found.get();
            if (cmd.getPlcCode() != ack.cmd()) {
                log.warn("Phản hồi reqId={} có mã lệnh {} khác lệnh đã gửi {}, bỏ qua", ack.reqId(), ack.cmd(), cmd.getPlcCode());
                return;
            }
            if (!apply(cmd, ack)) return;
            cmd = repo.save(cmd);
        }
        log.info("Lệnh #{} {} -> {} (result={})", cmd.getId(), cmd.getType(), cmd.getStatus(), ack.result());
        realtime.commandUpdated(CommandResponse.of(cmd));
    }

    private boolean apply(DeviceCommand cmd, CommandAckReceived ack) {
        CommandStatus s = cmd.getStatus();
        boolean lateExecuted = s == CommandStatus.TIMEOUT && ack.result() == 3;
        if (s.isFinal() && !lateExecuted) {
            return false;
        }
        Instant at = ack.receivedAt();
        switch (ack.result()) {
            case 1 -> {
                if (s != CommandStatus.SENT) return false;
                cmd.markAccepted(at);
            }
            case 2 -> cmd.finish(CommandStatus.REJECTED, 2,
                    orDefault(ack.msg(), "PLC từ chối lệnh (liên động an toàn hoặc sai chế độ)"), at);
            case 3 -> cmd.finish(CommandStatus.EXECUTED, 3,
                    lateExecuted ? "PLC báo đã thực hiện sau khi quá thời gian chờ" : ack.msg(), at);
            case 4 -> cmd.finish(CommandStatus.FAILED, 4, orDefault(ack.msg(), "PLC báo lỗi khi thực hiện lệnh"), at);
            case 10 -> cmd.finish(CommandStatus.EXPIRED, 10,
                    orDefault(ack.msg(), "Lệnh tới Gateway khi đã hết hạn nên không được thực hiện"), at);
            case 11 -> cmd.finish(CommandStatus.FAILED, 11,
                    orDefault(ack.msg(), "Gateway không giao tiếp được với PLC"), at);
            default -> {
                log.warn("Mã kết quả không xác định {} cho lệnh #{}", ack.result(), cmd.getId());
                return false;
            }
        }
        return true;
    }

    /** Lệnh không có phản hồi sau thời gian chờ -> TIMEOUT. Không gửi lại. */
    @Scheduled(fixedDelay = 2000)
    public void expireStaleCommands() {
        Instant now = Instant.now();
        for (DeviceCommand cmd : repo.findByStatusIn(CommandStatus.IN_FLIGHT)) {
            Instant since = cmd.getSentAt() != null ? cmd.getSentAt() : cmd.getCreatedAt();
            if (since.plus(ackTimeout).isBefore(now)) {
                synchronized (lock) {
                    DeviceCommand fresh = repo.findById(cmd.getId()).orElse(null);
                    if (fresh == null || fresh.getStatus().isFinal()) continue;
                    fresh.finish(CommandStatus.TIMEOUT, null,
                            "Không nhận được phản hồi từ trang trại. Lệnh không được gửi lại tự động.", now);
                    cmd = repo.save(fresh);
                }
                log.warn("Lệnh #{} {} hết thời gian chờ phản hồi", cmd.getId(), cmd.getType());
                realtime.commandUpdated(CommandResponse.of(cmd));
            }
        }
    }

    /** Xác nhận lệnh đã thực hiện bằng trạng thái thực tế trong telemetry (D100..D104). */
    public void confirmFromSnapshot(FarmSnapshot snap) {
        Instant after = snap.receivedAt().minus(confirmWindow);
        for (DeviceCommand cmd : repo.findByStatusAndConfirmedAtIsNullAndCompletedAtAfter(CommandStatus.EXECUTED, after)) {
            boolean matches = cmd.getType().expected().entrySet().stream()
                    .allMatch(e -> snap.isOn(e.getKey()) == e.getValue());
            if (matches) {
                cmd.confirm(snap.receivedAt());
                repo.save(cmd);
                realtime.commandUpdated(CommandResponse.of(cmd));
            }
        }
    }

    /** Tìm lệnh từ xa gần nhất có thể đã gây ra việc thiết bị đổi trạng thái (để ghi lịch sử "ai điều khiển"). */
    public Optional<DeviceCommand> findLikelyCause(Device device, boolean turnedOn, Instant at) {
        return repo.findByCreatedAtAfterAndStatusInOrderByIdDesc(at.minus(Duration.ofSeconds(60)),
                        java.util.EnumSet.of(CommandStatus.SENT, CommandStatus.ACCEPTED, CommandStatus.EXECUTED))
                .stream()
                .filter(c -> {
                    Map<Device, Boolean> exp = c.getType().expected();
                    return exp.containsKey(device) && exp.get(device) == turnedOn;
                })
                .findFirst();
    }

    public DeviceCommand get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("lệnh"));
    }

    private static String orDefault(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }
}
