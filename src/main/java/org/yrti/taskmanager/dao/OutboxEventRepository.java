package org.yrti.taskmanager.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yrti.taskmanager.model.OutboxEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
            SELECT e.* FROM outbox_events e
            WHERE ((e.status = 'PENDING' AND e.next_attempt_at <= :now)
                OR (e.status = 'PROCESSING' AND e.lease_until <= :now))
              AND NOT EXISTS (SELECT 1 FROM outbox_events earlier
                  WHERE earlier.aggregate_id = e.aggregate_id AND earlier.id < e.id
                    AND earlier.status <> 'PUBLISHED')
            ORDER BY e.id
            LIMIT :limit FOR UPDATE OF e SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockClaimable(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query(value = """
            UPDATE outbox_events SET status = 'PUBLISHED', published_at = :now,
                last_error = NULL, claim_token = NULL, lease_until = NULL
            WHERE id = :id AND status = 'PROCESSING' AND claim_token = :token AND lease_until > :now
            """, nativeQuery = true)
    int publishOwned(@Param("id") Long id, @Param("token") UUID token, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE outbox_events SET status = :status, next_attempt_at = :nextAttempt,
                last_error = :error, claim_token = NULL, lease_until = NULL
            WHERE id = :id AND status = 'PROCESSING' AND claim_token = :token AND lease_until > :now
            """, nativeQuery = true)
    int failOwned(@Param("id") Long id, @Param("token") UUID token, @Param("now") Instant now,
                  @Param("status") String status, @Param("nextAttempt") Instant nextAttempt,
                  @Param("error") String error);

    @Modifying
    @Query(value = "DELETE FROM outbox_events WHERE status = 'PUBLISHED' AND published_at < :cutoff", nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
