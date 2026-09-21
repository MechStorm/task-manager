package com.javarush.fedorov.taskmanager.metrics;

import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TaskMetrics {

    private final MeterRegistry registry;
    private final Counter created;
    private final Counter released;
    private final Counter deleted;

    public TaskMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.created = Counter.builder("tasks.creations").description("Created tasks").register(registry);
        this.released = Counter.builder("tasks.released").description("Released tasks to the pool").register(registry);
        this.deleted = Counter.builder("tasks.deleted").description("Deleted tasks").register(registry);

        for (TaskStatus prevStatus : TaskStatus.values()) {
            for (TaskStatus newStatus : TaskStatus.values()) {
                if (prevStatus != newStatus && prevStatus.canTransition(newStatus)) {
                    transitions(prevStatus, newStatus);
                }
            }
        }
    }

    public void taskCreated() { created.increment(); }
    public void taskReleased() { released.increment(); }
    public void taskDeleted() { deleted.increment(); }

    public void statusChanged(TaskStatus prevStatus, TaskStatus newStatus) {
        transitions(prevStatus, newStatus).increment();
    }

    private Counter transitions(TaskStatus prevStatus, TaskStatus newStatus) {
        return Counter.builder("tasks.status.transitions")
                .description("Task status transitions")
                .tag("prev_status", prevStatus.name())
                .tag("new_status", newStatus.name())
                .register(registry);
    }
}
