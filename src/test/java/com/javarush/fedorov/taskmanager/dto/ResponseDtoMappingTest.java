package com.javarush.fedorov.taskmanager.dto;

import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Mapping entities to API responses")
class ResponseDtoMappingTest {

    @Test
    @DisplayName("TaskResponseDto carries task and owner data")
    void mapsTaskWithOwner() {
        User owner = user();
        Instant deadline = Instant.now().plus(1, ChronoUnit.DAYS);
        Task task = task(TaskStatus.IN_PROGRESS, owner, deadline);

        TaskResponseDto dto = TaskResponseDto.from(task);

        assertThat(dto.getId()).isEqualTo(task.getId());
        assertThat(dto.getTitle()).isEqualTo("Task");
        assertThat(dto.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(dto.getDeadline()).isEqualTo(deadline);
        assertThat(dto.getOwnerId()).isEqualTo(owner.getId());
        assertThat(dto.getOwnerName()).isEqualTo(owner.getName());
    }

    @Test
    @DisplayName("a task without an owner has empty owner fields")
    void mapsTaskWithoutOwner() {
        TaskResponseDto dto = TaskResponseDto.from(task(TaskStatus.TO_DO, null, null));

        assertThat(dto.getOwnerId()).isNull();
        assertThat(dto.getOwnerName()).isNull();
        assertThat(dto.getDeadline()).isNull();
        assertThat(dto.getStatus()).isEqualTo(TaskStatus.TO_DO);
    }

    @Test
    @DisplayName("UserResponseDto exposes neither password nor role")
    void mapsUserWithoutSecrets() {
        User user = user();

        UserResponseDto dto = UserResponseDto.from(user);

        assertThat(dto.getId()).isEqualTo(user.getId());
        assertThat(dto.getName()).isEqualTo(user.getName());
        assertThat(dto.getEmail()).isEqualTo(user.getEmail());
        assertThat(dto).hasNoNullFieldsOrPropertiesExcept("createdAt", "updatedAt");
        assertThat(dto.toString())
                .as("the string representation must not expose the password")
                .doesNotContain(user.getPassword());
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Owner");
        user.setEmail("owner@example.com");
        user.setPassword("$2a$10$hash");
        user.setRole(Role.USER);
        return user;
    }

    private static Task task(TaskStatus status, User owner, Instant deadline) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setTitle("Task");
        task.setStatus(status);
        task.setOwner(owner);
        task.setDeadline(deadline);
        return task;
    }
}
