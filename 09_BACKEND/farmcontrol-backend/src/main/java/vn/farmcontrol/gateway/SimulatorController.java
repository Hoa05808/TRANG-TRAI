package vn.farmcontrol.gateway;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.farmcontrol.gateway.SimulatedGatewayClient.Fault;

/** Chỉ có khi farm.gateway.mode=simulator: tạo sự cố giả để thử màn hình cảnh báo trên app. */
@RestController
@RequestMapping("/api/simulator")
@ConditionalOnProperty(name = "farm.gateway.mode", havingValue = "simulator")
@PreAuthorize("hasRole('OWNER')")
public class SimulatorController {

    private final SimulatedGatewayClient simulator;

    public SimulatorController(SimulatedGatewayClient simulator) {
        this.simulator = simulator;
    }

    @GetMapping("/fault")
    public Map<String, Fault> get() {
        return Map.of("fault", simulator.getFault());
    }

    /** Ví dụ: PUT /api/simulator/fault?value=TEMP_HIGH ; value=NONE để trở lại bình thường. */
    @PutMapping("/fault")
    public Map<String, Fault> set(@RequestParam Fault value) {
        simulator.setFault(value);
        return Map.of("fault", simulator.getFault());
    }
}
