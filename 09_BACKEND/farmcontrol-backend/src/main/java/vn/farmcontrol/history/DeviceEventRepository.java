package vn.farmcontrol.history;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.farmcontrol.plc.Device;

public interface DeviceEventRepository extends JpaRepository<DeviceEvent, Long> {

    @Query("""
            select e from DeviceEvent e
            where e.occurredAt >= :from and e.occurredAt < :to
              and (:device is null or e.device = :device)
            order by e.occurredAt desc""")
    Page<DeviceEvent> search(@Param("from") Instant from, @Param("to") Instant to,
                             @Param("device") Device device, Pageable pageable);

    List<DeviceEvent> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(Instant from, Instant to);

    /** Sự kiện cuối cùng trước mốc thời gian (để biết trạng thái đầu kỳ khi tính thời gian chạy). */
    java.util.Optional<DeviceEvent> findFirstByDeviceAndOccurredAtLessThanOrderByOccurredAtDesc(Device device, Instant before);
}
