package com.javarush.fedorov.taskmanager.util;

import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import com.javarush.fedorov.taskmanager.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

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

        TaskStatus currentStatus = task.getStatus();

        if(!currentStatus.canTransition(newStatus)) {
            throw new IllegalArgumentException("Can't move task from " + currentStatus + " to " + newStatus);
        }
    }

    public static void assertCanModify(Task task, CurrentUser currentUser) {
        boolean isUnowned = task.getOwner() == null;
        boolean isOwner = task.getOwner() != null && task.getOwner().getId().equals(currentUser.id());

        if(!currentUser.isAdmin() && !isOwner && !isUnowned) {
            throw new AccessDeniedException("You can only modify your own or unassigned tasks");
        }
    }

    public static void assertCanAssignTo(UUID targetUserId, CurrentUser currentUser) {
        if(targetUserId == null) {
            return;
        }

        if(!currentUser.isAdmin() && !targetUserId.equals(currentUser.id())) {
            throw new AccessDeniedException("Only admin can assign tasks to other users");
        }
    }

    public static void assertCanEditContent(Task task, CurrentUser currentUser) {
        boolean isOwner = task.getOwner() != null && task.getOwner().getId().equals(currentUser.id());

        if(!currentUser.isAdmin() && !isOwner) {
            throw new AccessDeniedException("You can only edit tasks you own");
        }
    }
}
