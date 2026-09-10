package com.javarush.fedorov.taskmanager.util;

import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;

import java.time.Instant;

public class TaskValidationUtil {
    public static void validateDeadline(Instant deadline) {
        if (deadline != null && deadline.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Deadline can't be in the past");
        }
    }

    public static void validateStatusTransition(Task task, TaskStatus newStatus) {
        boolean requiresOwner = newStatus == TaskStatus.IN_PROGRESS || newStatus == TaskStatus.DONE;

        if (requiresOwner && task.getOwner() == null) {
            throw new IllegalArgumentException("Can't move task to " + newStatus + " because owner is null");
        }
    }
}
