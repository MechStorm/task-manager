package com.javarush.fedorov.taskmanager.model.repository;

import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.Task;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("Persistence layer: migrations, queries and optimistic locking")
class RepositoryIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("findByEmail finds a user and returns an empty Optional for an unknown address")
    void findsUserByEmail() {
        Actor alice = createUser("alice@repo.test");

        assertThat(userRepository.findByEmail("alice@repo.test"))
                .isPresent()
                .get()
                .extracting(User::getId)
                .isEqualTo(alice.id());

        assertThat(userRepository.findByEmail("nobody@repo.test")).isEmpty();
    }

    @Test
    @DisplayName("migrations created the schema: id, createdAt, updatedAt and version are filled in")
    void populatesAuditFields() {
        Actor alice = createUser("audit@repo.test");
        UUID taskId = createTask(alice, "Task", alice.id());

        Task task = taskRepository.findById(taskId).orElseThrow();

        assertThat(task.getId()).isNotNull();
        assertThat(task.getCreatedAt()).isNotNull().isBefore(Instant.now().plusSeconds(1));
        assertThat(task.getUpdatedAt()).isNotNull();
        assertThat(task.getVersion()).isZero();
    }

    @Test
    @DisplayName("version grows on every change")
    void incrementsVersionOnUpdate() {
        Actor alice = createUser("version@repo.test");
        UUID taskId = createTask(alice, "Task", alice.id());

        startTask(alice, taskId);

        assertThat(taskRepository.findById(taskId).orElseThrow().getVersion()).isPositive();
    }

    @Test
    @DisplayName("a concurrent write from a stale copy fails with an optimistic locking error")
    void detectsConcurrentModification() {
        Actor alice = createUser("locking@repo.test");
        UUID taskId = createTask(alice, "Task", alice.id());

        Task firstCopy = taskRepository.findById(taskId).orElseThrow();
        Task staleCopy = taskRepository.findById(taskId).orElseThrow();

        firstCopy.setTitle("Changed by the first writer");
        taskRepository.saveAndFlush(firstCopy);

        staleCopy.setTitle("Changed by the second writer");
        assertThatExceptionOfType(OptimisticLockingFailureException.class)
                .isThrownBy(() -> taskRepository.saveAndFlush(staleCopy));

        assertThat(taskRepository.findById(taskId).orElseThrow().getTitle()).isEqualTo("Changed by the first writer");
    }

    @Test
    @DisplayName("the unique index prevents a second account with the same email")
    void enforcesUniqueEmail() {
        createUser("unique@repo.test");

        User duplicate = new User();
        duplicate.setName("Duplicate");
        duplicate.setEmail("unique@repo.test");
        duplicate.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        duplicate.setRole(Role.USER);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> userRepository.saveAndFlush(duplicate));
    }

    @Test
    @DisplayName("findByOwnerId returns only the tasks of the given user")
    void findsTasksByOwner() {
        Actor alice = createUser("alice-tasks@repo.test");
        Actor bob = createUser("bob-tasks@repo.test");
        Actor admin = createAdmin("admin-tasks@repo.test");

        UUID aliceTask = createTask(admin, "Alice's task", alice.id());
        createTask(admin, "Bob's task", bob.id());
        createUnassignedTask(admin, "Free task");

        assertThat(taskRepository.findByOwnerId(alice.id()))
                .extracting(Task::getId)
                .containsExactly(aliceTask);

        assertThat(taskRepository.findByOwnerId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findByOwner_IdAndStatusIn filters tasks by owner and status set")
    void findsTasksByOwnerAndStatuses() {
        Actor alice = createUser("statuses@repo.test");
        Actor admin = createAdmin("admin-statuses@repo.test");

        UUID todo = createTask(admin, "Not started", alice.id());
        UUID inProgress = createTask(admin, "In progress", alice.id());
        startTask(alice, inProgress);

        assertThat(taskRepository.findByOwner_IdAndStatusIn(alice.id(),
                List.of(TaskStatus.TO_DO, TaskStatus.IN_PROGRESS)))
                .extracting(Task::getId)
                .containsExactlyInAnyOrder(todo, inProgress);

        assertThat(taskRepository.findByOwner_IdAndStatusIn(alice.id(), List.of(TaskStatus.DONE))).isEmpty();
    }

    @Test
    @DisplayName("findAll fetches the owner in one query: touching it outside a transaction is safe")
    void findAllFetchesOwnerEagerly() {
        Actor alice = createUser("graph@repo.test");
        createTask(alice, "Task", alice.id());

        List<Task> tasks = taskRepository.findAll();

        assertThat(tasks).hasSize(1);
        assertThatCode(() -> assertThat(tasks.getFirst().getOwner().getName()).isEqualTo(alice.name()))
                .as("@EntityGraph must initialize the owner")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the deadline survives a round trip through the database unchanged")
    void storesDeadlineWithoutDrift() {
        Actor alice = createUser("deadline@repo.test");
        Instant deadline = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        Task task = new Task();
        task.setTitle("With a deadline");
        task.setStatus(TaskStatus.TO_DO);
        task.setDeadline(deadline);
        task.setOwner(userRepository.findById(alice.id()).orElseThrow());
        UUID id = taskRepository.saveAndFlush(task).getId();

        assertThat(taskRepository.findById(id).orElseThrow().getDeadline()).isEqualTo(deadline);
    }
}
