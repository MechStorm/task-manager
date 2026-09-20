package com.javarush.fedorov.taskmanager.util;

import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import com.javarush.fedorov.taskmanager.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("TaskValidationUtil — task access and validation rules")
class TaskValidationUtilTest {

    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), Role.ADMIN);
    private static final CurrentUser OWNER = new CurrentUser(UUID.randomUUID(), Role.USER);
    private static final CurrentUser STRANGER = new CurrentUser(UUID.randomUUID(), Role.USER);

    @Nested
    @DisplayName("validateDeadline")
    class Deadline {

        @Test
        @DisplayName("null is allowed — the deadline is optional")
        void allowsNull() {
            assertThatCode(() -> TaskValidationUtil.validateDeadline(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a future date is allowed")
        void allowsFuture() {
            assertThatCode(() -> TaskValidationUtil.validateDeadline(Instant.now().plus(1, ChronoUnit.DAYS)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a past date is rejected")
        void rejectsPast() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> TaskValidationUtil.validateDeadline(Instant.now().minus(1, ChronoUnit.SECONDS)))
                    .withMessage("Deadline can't be in the past");
        }
    }

    @Nested
    @DisplayName("validateStatusTransition")
    class StatusTransition {

        @Test
        @DisplayName("a task without an owner cannot move to IN_PROGRESS")
        void requiresOwnerForInProgress() {
            Task task = task(TaskStatus.TO_DO, null);

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> TaskValidationUtil.validateStatusTransition(task, TaskStatus.IN_PROGRESS))
                    .withMessage("Can't move task to IN_PROGRESS because owner is null");
        }

        @Test
        @DisplayName("a task without an owner cannot move to DONE")
        void requiresOwnerForDone() {
            Task task = task(TaskStatus.IN_PROGRESS, null);

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> TaskValidationUtil.validateStatusTransition(task, TaskStatus.DONE))
                    .withMessage("Can't move task to DONE because owner is null");
        }

        @Test
        @DisplayName("moving back to TO_DO needs no owner")
        void allowsTodoWithoutOwner() {
            Task task = task(TaskStatus.TO_DO, null);

            assertThatCode(() -> TaskValidationUtil.validateStatusTransition(task, TaskStatus.TO_DO))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an allowed transition goes through")
        void allowsValidTransition() {
            Task task = task(TaskStatus.TO_DO, owner());

            assertThatCode(() -> TaskValidationUtil.validateStatusTransition(task, TaskStatus.IN_PROGRESS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a forbidden transition is rejected")
        void rejectsInvalidTransition() {
            Task task = task(TaskStatus.TO_DO, owner());

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> TaskValidationUtil.validateStatusTransition(task, TaskStatus.DONE))
                    .withMessage("Can't move task from TO_DO to DONE");
        }
    }

    @Nested
    @DisplayName("assertCanModify")
    class Modify {

        @Test
        @DisplayName("the owner may modify their own task")
        void allowsOwner() {
            assertThatCode(() -> TaskValidationUtil.assertCanModify(task(TaskStatus.TO_DO, owner()), OWNER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("admin may modify any task")
        void allowsAdmin() {
            assertThatCode(() -> TaskValidationUtil.assertCanModify(task(TaskStatus.TO_DO, owner()), ADMIN))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("anyone may modify a free task — it can be claimed from the pool")
        void allowsAnyoneForUnassignedTask() {
            assertThatCode(() -> TaskValidationUtil.assertCanModify(task(TaskStatus.TO_DO, null), STRANGER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("another user's assigned task cannot be modified")
        void rejectsStranger() {
            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> TaskValidationUtil.assertCanModify(task(TaskStatus.TO_DO, owner()), STRANGER))
                    .withMessage("You can only modify your own or unassigned tasks");
        }
    }

    @Nested
    @DisplayName("assertCanAssignTo")
    class AssignTo {

        @Test
        @DisplayName("null means no reassignment")
        void allowsNullTarget() {
            assertThatCode(() -> TaskValidationUtil.assertCanAssignTo(null, STRANGER)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("assigning to yourself is allowed")
        void allowsSelfAssignment() {
            assertThatCode(() -> TaskValidationUtil.assertCanAssignTo(OWNER.id(), OWNER)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("admin assigns to anyone")
        void allowsAdminAssignment() {
            assertThatCode(() -> TaskValidationUtil.assertCanAssignTo(OWNER.id(), ADMIN)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a regular user does not assign to others")
        void rejectsAssignmentToOthers() {
            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> TaskValidationUtil.assertCanAssignTo(OWNER.id(), STRANGER))
                    .withMessage("Only admin can assign tasks to other users");
        }
    }

    @Nested
    @DisplayName("assertCanEditContent")
    class EditContent {

        @Test
        @DisplayName("the owner edits the content of their own task")
        void allowsOwner() {
            assertThatCode(() -> TaskValidationUtil.assertCanEditContent(task(TaskStatus.TO_DO, owner()), OWNER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("admin edits the content of any task")
        void allowsAdmin() {
            assertThatCode(() -> TaskValidationUtil.assertCanEditContent(task(TaskStatus.TO_DO, owner()), ADMIN))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a user cannot edit the content of a free task")
        void rejectsUnassignedTaskForUser() {
            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> TaskValidationUtil.assertCanEditContent(task(TaskStatus.TO_DO, null), STRANGER))
                    .withMessage("You can only edit tasks you own");
        }

        @Test
        @DisplayName("another user's task cannot be edited")
        void rejectsStranger() {
            assertThatExceptionOfType(AccessDeniedException.class)
                    .isThrownBy(() -> TaskValidationUtil.assertCanEditContent(task(TaskStatus.TO_DO, owner()), STRANGER))
                    .withMessage("You can only edit tasks you own");
        }
    }

    private static User owner() {
        User user = new User();
        user.setId(OWNER.id());
        user.setName("Owner");
        user.setEmail("owner@example.com");
        user.setPassword("hash");
        user.setRole(Role.USER);
        return user;
    }

    private static Task task(TaskStatus status, User owner) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setTitle("Task");
        task.setStatus(status);
        task.setOwner(owner);
        return task;
    }
}
