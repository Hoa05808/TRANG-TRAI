package vn.farmcontrol.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import vn.farmcontrol.common.ApiException;

/** Gom dữ liệu cảm biến theo khoảng thời gian cho màn hình Biểu đồ. */
@Service
public class SensorHistoryService {

    /** Khoảng xem mặc định và độ rộng mỗi điểm trên biểu đồ. */
    public enum Range {
        HOUR(Duration.ofHours(1), Duration.ofMinutes(1)),
        DAY(Duration.ofDays(1), Duration.ofMinutes(15)),
        WEEK(Duration.ofDays(7), Duration.ofHours(2)),
        MONTH(Duration.ofDays(30), Duration.ofHours(6));

        final Duration length;
        final Duration bucket;

        Range(Duration length, Duration bucket) {
            this.length = length;
            this.bucket = bucket;
        }
    }

    public record Stat(double avg, double min, double max) {
    }

    public record HistoryPoint(Instant time, int samples, Stat coopTemp, Stat airHumidity, Stat waterLevel,
                               Stat waterTemp, Stat soilHumidity, Stat co2Ppm, Stat nh3Ppm, Stat lightLux) {
    }

    public record HistoryResponse(Instant from, Instant to, long bucketSeconds, List<HistoryPoint> points) {
    }

    private static final int MAX_POINTS = 1000;

    private final SensorReadingRepository repo;

    public SensorHistoryService(SensorReadingRepository repo) {
        this.repo = repo;
    }

    public HistoryResponse history(Range range, Instant from, Instant to, Integer bucketMinutes) {
        Instant t = to != null ? to : Instant.now();
        Instant f = from != null ? from : t.minus(range.length);
        if (!f.isBefore(t)) throw ApiException.badRequest("INVALID_RANGE", "Thời gian bắt đầu phải trước thời gian kết thúc");
        if (Duration.between(f, t).toDays() > 400) throw ApiException.badRequest("RANGE_TOO_LARGE", "Khoảng thời gian tối đa 400 ngày");

        Duration bucket = bucketMinutes != null ? Duration.ofMinutes(Math.max(1, bucketMinutes)) : range.bucket;
        long total = Duration.between(f, t).toSeconds();
        if (total / bucket.toSeconds() > MAX_POINTS) {
            bucket = Duration.ofSeconds((long) Math.ceil((double) total / MAX_POINTS));
        }
        long bucketSec = bucket.toSeconds();
        long base = f.getEpochSecond() - Math.floorMod(f.getEpochSecond(), bucketSec);

        List<SensorReading> rows = repo
                .findByRecordedAtGreaterThanEqualAndRecordedAtLessThanAndDataValidTrueOrderByRecordedAtAsc(f, t);
        List<HistoryPoint> points = new ArrayList<>();
        List<SensorReading> current = new ArrayList<>();
        long currentKey = Long.MIN_VALUE;
        for (SensorReading r : rows) {
            long key = (r.getRecordedAt().getEpochSecond() - base) / bucketSec;
            if (key != currentKey && !current.isEmpty()) {
                points.add(aggregate(Instant.ofEpochSecond(base + currentKey * bucketSec), current));
                current = new ArrayList<>();
            }
            currentKey = key;
            current.add(r);
        }
        if (!current.isEmpty()) points.add(aggregate(Instant.ofEpochSecond(base + currentKey * bucketSec), current));
        return new HistoryResponse(f, t, bucketSec, points);
    }

    private static HistoryPoint aggregate(Instant time, List<SensorReading> rs) {
        return new HistoryPoint(time, rs.size(),
                stat(rs, SensorReading::getCoopTemp), stat(rs, SensorReading::getAirHumidity),
                stat(rs, SensorReading::getWaterLevel), stat(rs, SensorReading::getWaterTemp),
                stat(rs, SensorReading::getSoilHumidity),
                stat(rs, r -> r.getCo2Ppm() == null ? null : r.getCo2Ppm().doubleValue()),
                stat(rs, r -> r.getNh3Ppm() == null ? null : r.getNh3Ppm().doubleValue()),
                stat(rs, r -> r.getLightLux() == null ? null : r.getLightLux().doubleValue()));
    }

    private static Stat stat(List<SensorReading> rs, Function<SensorReading, Double> f) {
        double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int n = 0;
        for (SensorReading r : rs) {
            Double v = f.apply(r);
            if (v == null) continue;
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
            n++;
        }
        if (n == 0) return null;
        return new Stat(Math.round(sum / n * 10) / 10.0, min, max);
    }
}
