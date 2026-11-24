package vn.farmcontrol.state;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard() {
        return dashboard.build();
    }

    /** Kiểm tra máy chủ còn sống (không cần đăng nhập). */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "time", Instant.now());
    }
}
