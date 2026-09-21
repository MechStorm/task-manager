package com.javarush.fedorov.taskmanager.controller;

import com.javarush.fedorov.taskmanager.dto.CreateTaskRequestDto;
import com.javarush.fedorov.taskmanager.dto.TaskResponseDto;
import com.javarush.fedorov.taskmanager.dto.UpdateTaskRequestDto;
import com.javarush.fedorov.taskmanager.model.entity.UserSecureWrapper;
import com.javarush.fedorov.taskmanager.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Tasks and the shared pool of unassigned tasks")
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "Get all tasks")
    @ApiResponse(responseCode = "200", description = "All tasks, assigned and unassigned")
    @GetMapping
    public ResponseEntity<List<TaskResponseDto>> findAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    @Operation(summary = "Get a task by id")
    @ApiResponse(responseCode = "200", description = "The task")
    @ApiResponse(responseCode = "404", description = "No task with this id")
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponseDto> findTaskById(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @Operation(summary = "Create a task",
            description = "A new task always starts in `TO_DO`. Without `userId` it goes to the shared pool. "
                    + "A `USER` may assign the task only to themselves; assigning it to someone else requires `ADMIN`.")
    @ApiResponse(responseCode = "201", description = "Task created",
            headers = @Header(name = "Location", description = "URI of the created task",
                    schema = @Schema(type = "string", example = "/api/tasks/3f2a1c4e-0000-0000-0000-000000000000")))
    @ApiResponse(responseCode = "400", description = "Invalid body or a deadline in the past")
    @ApiResponse(responseCode = "403", description = "A `USER` tried to assign the task to another user")
    @ApiResponse(responseCode = "404", description = "No user with the given `userId`")
    @PostMapping
    public ResponseEntity<TaskResponseDto> createTask(
            @Valid @RequestBody CreateTaskRequestDto requestDto,
            @AuthenticationPrincipal UserSecureWrapper userSecureWrapper
    ) {
        TaskResponseDto responseDto = taskService.createTask(requestDto, userSecureWrapper.toCurrentUser());
        URI location = URI.create("/api/tasks/" + responseDto.getId());
        return ResponseEntity.created(location).body(responseDto);
    }

    @Operation(summary = "Release a task back to the shared pool",
            description = "Removes the owner and resets the status to `TO_DO`. Allowed to the owner or an `ADMIN`.")
    @ApiResponse(responseCode = "200", description = "Task released")
    @ApiResponse(responseCode = "400", description = "The task is already unassigned or is `DONE`")
    @ApiResponse(responseCode = "403", description = "The caller is neither the owner nor an `ADMIN`")
    @ApiResponse(responseCode = "404", description = "No task with this id")
    @ApiResponse(responseCode = "409", description = "The task was modified concurrently; reload it and retry")
    @PostMapping("/{id}/release")
    public ResponseEntity<TaskResponseDto> releaseTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserSecureWrapper userSecureWrapper
    ) {
        TaskResponseDto responseDto = taskService.releaseTask(id, userSecureWrapper.toCurrentUser());
        return ResponseEntity.ok(responseDto);
    }

    @Operation(summary = "Partially update a task",
            description = "Fields that are not sent stay unchanged. Anyone may take an unassigned task "
                    + "(`userId` = own id) and move its status; `title` and `deadline` can be changed only "
                    + "by the owner or an `ADMIN`. Only an `ADMIN` may assign the task to another user.")
    @ApiResponse(responseCode = "200", description = "Task updated")
    @ApiResponse(responseCode = "400", description = "Invalid body, a deadline in the past, a forbidden status "
            + "transition, or `IN_PROGRESS`/`DONE` for a task without an owner")
    @ApiResponse(responseCode = "403", description = "Someone else's task, or assigning it to another user without `ADMIN`")
    @ApiResponse(responseCode = "404", description = "No task with this id, or no user with the given `userId`")
    @ApiResponse(responseCode = "409", description = "The task was modified concurrently; reload it and retry")
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponseDto> updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequestDto requestDto,
            @AuthenticationPrincipal UserSecureWrapper userSecureWrapper
    ) {
        TaskResponseDto responseDto = taskService.updateTask(id, requestDto, userSecureWrapper.toCurrentUser());
        return ResponseEntity.ok(responseDto);
    }

    @Operation(summary = "Delete a task", description = "`ADMIN` only.")
    @ApiResponse(responseCode = "204", description = "Task deleted")
    @ApiResponse(responseCode = "403", description = "The caller is not an `ADMIN`")
    @ApiResponse(responseCode = "404", description = "No task with this id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
