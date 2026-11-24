package vn.farmcontrol.setting;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.farmcontrol.auth.CurrentUser;
import vn.farmcontrol.setting.SettingsService.SettingView;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public List<SettingView> list() {
        return settings.list();
    }

    /** Body: {"alert.temp.high": 36.0, "alert.water.low": 15} */
    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public List<SettingView> update(@RequestBody Map<String, Double> changes, CurrentUser user) {
        return settings.update(changes, user.username());
    }
}
