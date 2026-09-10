package com.javarush.fedorov.taskmanager.controller;

import com.javarush.fedorov.taskmanager.dto.CreateTaskRequestDto;
import com.javarush.fedorov.taskmanager.dto.TaskResponseDto;
import com.javarush.fedorov.taskmanager.dto.UpdateTaskRequestDto;
import com.javarush.fedorov.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/tasks")
@Slf4j
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskResponseDto>> findAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponseDto> findTaskById(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @PostMapping
    public ResponseEntity<TaskResponseDto> createTask(@Valid @RequestBody CreateTaskRequestDto requestDto) {
        TaskResponseDto responseDto = taskService.createTask(requestDto);
        URI location = URI.create("/api/tasks/" + responseDto.getId());
        return ResponseEntity.created(location).body(responseDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponseDto> updateTask(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequestDto requestDto) {
        TaskResponseDto responseDto = taskService.updateTask(id, requestDto);
        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
