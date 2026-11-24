package vn.farmcontrol.telemetry;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import vn.farmcontrol.telemetry.SensorHistoryService.HistoryResponse;
import vn.farmcontrol.telemetry.SensorHistoryService.Range;

@RestController
@RequestMapping("/api/sensors")
public class SensorController {

    private final SensorHistoryService history;

    public SensorController(SensorHistoryService history) {
        this.history = history;
    }

    /**
     * Ví dụ: GET /api/sensors/history?range=DAY
     *        GET /api/sensors/history?from=2026-11-01T00:00:00Z&to=2026-11-08T00:00:00Z&bucketMinutes=60
     */
    @GetMapping("/history")
    public HistoryResponse history(
            @RequestParam(defaultValue = "DAY") Range range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer bucketMinutes) {
        return history.history(range, from, to, bucketMinutes);
    }
}
