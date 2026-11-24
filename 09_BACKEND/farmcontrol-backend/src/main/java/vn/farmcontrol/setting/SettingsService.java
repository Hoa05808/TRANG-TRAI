package vn.farmcontrol.setting;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farmcontrol.common.ApiException;

@Service
public class SettingsService {

    public record SettingView(String key, double value, double defaultValue, double min, double max,
                              String unit, String description) {
    }

    private final AppSettingRepository repo;
    private volatile Map<SettingKey, Double> cache;

    public SettingsService(AppSettingRepository repo) {
        this.repo = repo;
    }

    public double get(SettingKey key) {
        return values().getOrDefault(key, key.defaultValue());
    }

    public int getInt(SettingKey key) {
        return (int) Math.round(get(key));
    }

    public List<SettingView> list() {
        Map<SettingKey, Double> v = values();
        return java.util.Arrays.stream(SettingKey.values())
                .map(k -> new SettingView(k.key(), v.getOrDefault(k, k.defaultValue()), k.defaultValue(),
                        k.min(), k.max(), k.unit(), k.description()))
                .toList();
    }

    @Transactional
    public List<SettingView> update(Map<String, Double> changes, String username) {
        Map<SettingKey, Double> merged = new EnumMap<>(SettingKey.class);
        merged.putAll(values());
        for (Map.Entry<String, Double> e : changes.entrySet()) {
            SettingKey k = SettingKey.fromKey(e.getKey())
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_SETTING", "Không có thông số " + e.getKey()));
            Double val = e.getValue();
            if (val == null || val.isNaN() || val < k.min() || val > k.max()) {
                throw ApiException.badRequest("SETTING_OUT_OF_RANGE",
                        "%s phải nằm trong khoảng %s – %s %s".formatted(k.description(), fmt(k.min()), fmt(k.max()), k.unit()));
            }
            merged.put(k, val);
        }
        double low = merged.getOrDefault(SettingKey.ALERT_HUMIDITY_LOW, SettingKey.ALERT_HUMIDITY_LOW.defaultValue());
        double high = merged.getOrDefault(SettingKey.ALERT_HUMIDITY_HIGH, SettingKey.ALERT_HUMIDITY_HIGH.defaultValue());
        if (low >= high) {
            throw ApiException.badRequest("SETTING_CONFLICT", "Ngưỡng độ ẩm thấp phải nhỏ hơn ngưỡng độ ẩm cao");
        }
        for (Map.Entry<String, Double> e : changes.entrySet()) {
            String value = String.valueOf(e.getValue());
            repo.findById(e.getKey()).ifPresentOrElse(
                    s -> s.update(value, username),
                    () -> repo.save(new AppSetting(e.getKey(), value, username)));
        }
        cache = null;
        return list();
    }

    private Map<SettingKey, Double> values() {
        Map<SettingKey, Double> c = cache;
        if (c == null) {
            Map<SettingKey, Double> loaded = new HashMap<>();
            for (AppSetting s : repo.findAll()) {
                SettingKey.fromKey(s.getKey()).ifPresent(k -> {
                    try {
                        loaded.put(k, Double.parseDouble(s.getValue()));
                    } catch (NumberFormatException ignored) {
                        // giá trị hỏng trong DB -> dùng mặc định
                    }
                });
            }
            cache = c = Map.copyOf(loaded);
        }
        return c;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
