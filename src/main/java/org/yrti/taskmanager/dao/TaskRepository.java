package org.yrti.taskmanager.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByStatus(Status status);
    List<Task> findAllByCategory(Category category);
    List<Task> findAllByOwnerId(Long ownerId);
    boolean existsByOwnerId(Long ownerId);
}
