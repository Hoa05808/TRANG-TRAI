package vn.farmcontrol.command;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {

    boolean existsByStatusIn(Collection<CommandStatus> statuses);

    List<DeviceCommand> findByStatusIn(Collection<CommandStatus> statuses);

    Optional<DeviceCommand> findFirstByRequestIdAndCreatedAtAfterOrderByIdDesc(int requestId, Instant after);

    List<DeviceCommand> findByStatusAndConfirmedAtIsNullAndCompletedAtAfter(CommandStatus status, Instant after);

    List<DeviceCommand> findByCreatedAtAfterAndStatusInOrderByIdDesc(Instant after, Collection<CommandStatus> statuses);

    @Query("""
            select c from DeviceCommand c
            where c.createdAt >= :from and c.createdAt < :to
              and (:status is null or c.status = :status)
            order by c.id desc""")
    Page<DeviceCommand> search(@Param("from") Instant from, @Param("to") Instant to,
                               @Param("status") CommandStatus status, Pageable pageable);
}
