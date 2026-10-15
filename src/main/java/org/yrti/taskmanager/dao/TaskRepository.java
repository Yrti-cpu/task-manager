package org.yrti.taskmanager.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByStatus(Status status);
    List<Task> findAllByCategory(Category category);
    List<Task> findAllByOwnerId(Long ownerId);
    boolean existsByOwnerId(Long ownerId);

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
