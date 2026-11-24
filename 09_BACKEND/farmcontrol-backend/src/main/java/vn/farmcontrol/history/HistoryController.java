package vn.farmcontrol.history;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import vn.farmcontrol.command.CommandDtos.CommandResponse;
import vn.farmcontrol.command.CommandStatus;
import vn.farmcontrol.command.DeviceCommandRepository;
import vn.farmcontrol.common.ApiException;
import vn.farmcontrol.common.PageResponse;
import vn.farmcontrol.plc.Device;

/** Màn hình Lịch sử: ai điều khiển, thiết bị nào chạy, thời gian và kết quả. */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    public record DeviceEventResponse(Long id, Device device, String deviceName, boolean turnedOn,
                                      DeviceEvent.Source source, Long commandId, String username, Instant occurredAt) {
        static DeviceEventResponse of(DeviceEvent e) {
            return new DeviceEventResponse(e.getId(), e.getDevice(), e.getDevice().displayName(), e.isTurnedOn(),
                    e.getSource(), e.getCommandId(), e.getUsername(), e.getOccurredAt());
        }
    }

    public record Segment(Instant start, Instant end) {
    }

    public record DeviceRuntime(Device device, String deviceName, long onSeconds, int starts, List<Segment> segments) {
    }

    private final DeviceCommandRepository commands;
    private final DeviceEventRepository events;

    public HistoryController(DeviceCommandRepository commands, DeviceEventRepository events) {
        this.commands = commands;
        this.events = events;
    }

    @GetMapping("/commands")
    public PageResponse<CommandResponse> commands(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) CommandStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Instant t = to != null ? to : Instant.now().plusSeconds(1);
        Instant f = from != null ? from : t.minus(30, ChronoUnit.DAYS);
        return PageResponse.of(commands.search(f, t, status, page(page, size)), CommandResponse::of);
    }

    @GetMapping("/device-events")
    public PageResponse<DeviceEventResponse> deviceEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Device device,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Instant t = to != null ? to : Instant.now().plusSeconds(1);
        Instant f = from != null ? from : t.minus(7, ChronoUnit.DAYS);
        return PageResponse.of(events.search(f, t, device, page(page, size)), DeviceEventResponse::of);
    }

    /** Thời gian chạy của từng thiết bị trong khoảng (cho biểu đồ hoạt động thiết bị). Mặc định 24 giờ. */
    @GetMapping("/device-runtime")
    public List<DeviceRuntime> deviceRuntime(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant t = to != null ? to : Instant.now();
        Instant f = from != null ? from : t.minus(1, ChronoUnit.DAYS);
        if (!f.isBefore(t) || Duration.between(f, t).toDays() > 31) {
            throw ApiException.badRequest("INVALID_RANGE", "Khoảng thời gian phải hợp lệ và tối đa 31 ngày");
        }
        Map<Device, Instant> onAt = new EnumMap<>(Device.class);
        Map<Device, List<Segment>> segments = new EnumMap<>(Device.class);
        Map<Device, Integer> starts = new EnumMap<>(Device.class);
        for (Device d : Device.values()) {
            segments.put(d, new ArrayList<>());
            starts.put(d, 0);
            events.findFirstByDeviceAndOccurredAtLessThanOrderByOccurredAtDesc(d, f)
                    .filter(DeviceEvent::isTurnedOn)
                    .ifPresent(e -> onAt.put(d, f));
        }
        for (DeviceEvent e : events.findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(f, t)) {
            Device d = e.getDevice();
            if (e.isTurnedOn()) {
                if (!onAt.containsKey(d)) {
                    onAt.put(d, e.getOccurredAt());
                    starts.merge(d, 1, Integer::sum);
                }
            } else {
                Instant s = onAt.remove(d);
                if (s != null) segments.get(d).add(new Segment(s, e.getOccurredAt()));
            }
        }
        onAt.forEach((d, s) -> segments.get(d).add(new Segment(s, t))); // vẫn đang chạy tới cuối kỳ
        List<DeviceRuntime> result = new ArrayList<>();
        for (Device d : Device.values()) {
            long sec = segments.get(d).stream().mapToLong(s -> Duration.between(s.start(), s.end()).toSeconds()).sum();
            result.add(new DeviceRuntime(d, d.displayName(), sec, starts.get(d), segments.get(d)));
        }
        return result;
    }

    private static PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200));
    }
}
