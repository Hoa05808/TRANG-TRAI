package vn.farmcontrol.alert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.farmcontrol.auth.CurrentUser;
import vn.farmcontrol.common.PageResponse;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alerts;
    private final AlertRepository repo;

    public AlertController(AlertService alerts, AlertRepository repo) {
        this.alerts = alerts;
        this.repo = repo;
    }

    @GetMapping("/active")
    public List<AlertResponse> active() {
        return alerts.active();
    }

    /** Lịch sử cảnh báo. Mặc định 7 ngày gần nhất. */
    @GetMapping
    public PageResponse<AlertResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Alert.Status status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Instant t = to != null ? to : Instant.now().plusSeconds(1);
        Instant f = from != null ? from : t.minus(7, ChronoUnit.DAYS);
        return PageResponse.of(repo.search(f, t, status, severity,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200))), AlertResponse::of);
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("hasAnyRole('OWNER','OPERATOR')")
    public AlertResponse acknowledge(@PathVariable Long id, CurrentUser user) {
        return alerts.acknowledge(id, user.username());
    }
}
