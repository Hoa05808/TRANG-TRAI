package vn.farmcontrol.alert;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findFirstByTypeAndStatus(AlertType type, Alert.Status status);

    List<Alert> findByStatusOrderByRaisedAtDesc(Alert.Status status);

    long countByStatus(Alert.Status status);

    long countByStatusAndAcknowledgedAtIsNull(Alert.Status status);

    @Query("""
            select a from Alert a
            where a.raisedAt >= :from and a.raisedAt < :to
              and (:status is null or a.status = :status)
              and (:severity is null or a.severity = :severity)
            order by a.raisedAt desc""")
    Page<Alert> search(@Param("from") Instant from, @Param("to") Instant to,
                       @Param("status") Alert.Status status, @Param("severity") AlertSeverity severity,
                       Pageable pageable);
}
