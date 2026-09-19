package com.javarush.fedorov.taskmanager.service;

import com.javarush.fedorov.taskmanager.dto.CreateUserRequestDto;
import com.javarush.fedorov.taskmanager.dto.RegisterRequestDto;
import com.javarush.fedorov.taskmanager.dto.UpdateUserRequestDto;
import com.javarush.fedorov.taskmanager.dto.UserResponseDto;
import com.javarush.fedorov.taskmanager.exception.EmailAlreadyInUseException;
import com.javarush.fedorov.taskmanager.exception.ResourceNotFoundException;
import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.repository.TaskRepository;
import com.javarush.fedorov.taskmanager.model.repository.UserRepository;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import com.javarush.fedorov.taskmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponseDto register(RegisterRequestDto requestDto) {
        if(userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new EmailAlreadyInUseException("Email already in use");
        }

        User user = new User();
        user.setName(requestDto.getName());
        user.setEmail(requestDto.getEmail());
        user.setPassword(passwordEncoder.encode(requestDto.getPassword()));
        user.setRole(Role.USER);

        User savedUser = userRepository.saveAndFlush(user);
        log.info("User {} registered with role {}", savedUser.getId(), savedUser.getRole());

        return UserResponseDto.from(savedUser);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponseDto createUser(CreateUserRequestDto requestDto) {
        if (userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new EmailAlreadyInUseException("Email already in use");
        }

        User user = new User();
        user.setName(requestDto.getName());
        user.setEmail(requestDto.getEmail());
        user.setPassword(passwordEncoder.encode(requestDto.getPassword()));
        user.setRole(requestDto.getRole() != null ? requestDto.getRole() : Role.USER);

        User savedUser = userRepository.saveAndFlush(user);
        log.info("User created: {}", savedUser);

        return UserResponseDto.from(savedUser);
    }

    @Transactional
    public UserResponseDto updateUser(UUID userId, UpdateUserRequestDto requestDto, CurrentUser currentUser) {

        if(!currentUser.isAdmin() && !currentUser.id().equals(userId)) {
            throw new AccessDeniedException("You can only update your own profile");
        }

        User user = findUserEntity(userId);

        if (requestDto.getName() != null && !requestDto.getName().isEmpty()) {
            user.setName(requestDto.getName());
            log.info("User name updated: {}", user.getName());
        }

        if (requestDto.getPassword() != null && !requestDto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(requestDto.getPassword()));
        }

        User savedUser = userRepository.saveAndFlush(user);
        log.info("User updated: {}", savedUser);

        return UserResponseDto.from(savedUser);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteUser(UUID id) {
        log.info("Deleting user with id {}", id);

        User user = findUserEntity(id);

        List<Task> activeTasks = taskRepository.findByOwner_IdAndStatusIn(id, List.of(TaskStatus.TO_DO, TaskStatus.IN_PROGRESS));
        log.info("User {} has {} active tasks to unassign", id, activeTasks.size());

        activeTasks.forEach(task -> {
            task.setOwner(null);
            task.setStatus(TaskStatus.TO_DO);
        });
        taskRepository.saveAll(activeTasks);

        userRepository.deleteById(id);
        log.info("User {} deleted. {} tasks moved to TO_DO", id, activeTasks.size());
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> findAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponseDto findUserById(UUID id) {
        return UserResponseDto.from(findUserEntity(id));
    }


    private User findUserEntity(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User {} not found", userId);
                    return new ResourceNotFoundException("User not found: " + userId);
                });
    }
}
