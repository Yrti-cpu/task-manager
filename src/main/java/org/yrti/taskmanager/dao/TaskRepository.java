package org.yrti.taskmanager.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Task> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<Task> findAllByStatus(Status status, Pageable pageable);
    @EntityGraph(attributePaths = "owner")
    Page<Task> findAllByCategory(Category category, Pageable pageable);
    @EntityGraph(attributePaths = "owner")
    Page<Task> findAllByOwnerId(Long ownerId, Pageable pageable);
    boolean existsByOwnerId(Long ownerId);

    @EntityGraph(attributePaths = "owner")
    @Query("""
            select t from Task t
            where (:status is null or t.status = :status)
              and (:from is null or t.dueAt >= :from)
              and (:to is null or t.dueAt <= :to)
            """)
    Page<Task> search(
            @Param("status") Status status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
