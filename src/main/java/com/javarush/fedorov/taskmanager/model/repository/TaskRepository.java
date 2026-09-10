package com.javarush.fedorov.taskmanager.model.repository;

import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByOwnerId(UUID ownerId);

    List<Task> findByOwner_IdAndStatusIn(UUID ownerId, List<TaskStatus> statuses);

    @Override
    @EntityGraph(attributePaths = "owner")
    List<Task> findAll();
}
