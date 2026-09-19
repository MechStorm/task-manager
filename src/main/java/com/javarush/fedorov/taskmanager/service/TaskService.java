package com.javarush.fedorov.taskmanager.service;

import com.javarush.fedorov.taskmanager.dto.CreateTaskRequestDto;
import com.javarush.fedorov.taskmanager.dto.TaskResponseDto;
import com.javarush.fedorov.taskmanager.dto.UpdateTaskRequestDto;
import com.javarush.fedorov.taskmanager.exception.ResourceNotFoundException;
import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.repository.TaskRepository;
import com.javarush.fedorov.taskmanager.model.repository.UserRepository;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import com.javarush.fedorov.taskmanager.security.CurrentUser;
import com.javarush.fedorov.taskmanager.util.TaskValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public TaskResponseDto createTask(CreateTaskRequestDto requestDto, CurrentUser currentUser) {

        TaskValidationUtil.validateDeadline(requestDto.getDeadline());
        TaskValidationUtil.assertCanAssignTo(requestDto.getUserId(), currentUser);

        User owner = resolveUserOrNull(requestDto.getUserId());

        Task task = new Task();
        task.setTitle(requestDto.getTitle());
        task.setDeadline(requestDto.getDeadline());
        task.setOwner(owner);
        task.setStatus(TaskStatus.TO_DO);

        Task savedTask = taskRepository.saveAndFlush(task);
        log.info("Created task {} with status {}", savedTask.getId(), savedTask.getStatus());

        return TaskResponseDto.from(savedTask);
    }

    @Transactional
    public TaskResponseDto updateTask(UUID taskId, UpdateTaskRequestDto requestDto, CurrentUser currentUser) {
        Task task = findTaskEntityById(taskId);

        TaskValidationUtil.assertCanModify(task, currentUser);
        TaskValidationUtil.assertCanAssignTo(requestDto.getUserId(), currentUser);

        if (requestDto.getUserId() != null) {
            User newOwner = userRepository.findById(requestDto.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requestDto.getUserId()));
            task.setOwner(newOwner);
            log.info("Task {} reassigned to user {}", taskId, newOwner.getId());
        }

        if (requestDto.getTitle() != null) {
            TaskValidationUtil.assertCanEditContent(task, currentUser);
            task.setTitle(requestDto.getTitle());
        }

        if (requestDto.getDeadline() != null) {
            TaskValidationUtil.assertCanEditContent(task, currentUser);
            TaskValidationUtil.validateDeadline(requestDto.getDeadline());
            Instant previousDeadline = task.getDeadline();
            task.setDeadline(requestDto.getDeadline());
            log.info("Updated task {} with deadline {}. Previous deadline: {}",
                    taskId, task.getDeadline(), previousDeadline);
        }


        if (requestDto.getStatus() != null) {
            TaskValidationUtil.validateStatusTransition(task, requestDto.getStatus());
            TaskStatus previousStatus = task.getStatus();
            task.setStatus(requestDto.getStatus());
            log.info("Task {} reassigned from status {} to status {}", taskId, previousStatus, requestDto.getStatus());
        }

        Task savedTask = taskRepository.saveAndFlush(task);
        log.info("Updated task {} with status {}", taskId, savedTask.getStatus());
        return TaskResponseDto.from(savedTask);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteTask(UUID taskId) {
        Task task = findTaskEntityById(taskId);

        log.info("Deleted task {} with status {}", taskId, task.getStatus());
        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public TaskResponseDto getTaskById(UUID taskId) {
        return TaskResponseDto.from(findTaskEntityById(taskId));
    }

    @Transactional(readOnly = true)
    public List<TaskResponseDto> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(TaskResponseDto::from)
                .toList();
    }

    @Transactional
    public TaskResponseDto releaseTask(UUID taskId, CurrentUser currentUser) {
        Task task = findTaskEntityById(taskId);

        TaskValidationUtil.assertCanEditContent(task, currentUser);

        if(task.getStatus() == TaskStatus.DONE) {
            throw new IllegalArgumentException("Completed task can't be released back to the pool");
        }

        UUID previousOwnerId = task.getOwner().getId();
        task.setOwner(null);
        task.setStatus(TaskStatus.TO_DO);

        Task savedTask = taskRepository.saveAndFlush(task);
        log.info("Released task {} by user {} back to the pull", taskId, previousOwnerId);

        return TaskResponseDto.from(savedTask);
    }

    private Task findTaskEntityById(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> {
                    log.warn("Task {} not found", taskId);
                    return new ResourceNotFoundException("Task with id " + taskId + " not found");
                });
    }

    private User resolveUserOrNull(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
