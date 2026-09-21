package com.javarush.fedorov.taskmanager.metrics;

import com.javarush.fedorov.taskmanager.model.status.TaskStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskMetrics — task counters")
class TaskMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final TaskMetrics metrics = new TaskMetrics(registry);

    @ParameterizedTest(name = "{0} is registered at zero")
    @ValueSource(strings = {"tasks.creations", "tasks.released", "tasks.deleted"})
    void registersTaskCountersUpFront(String name) {
        assertThat(registry.get(name).counter().count()).isZero();
    }

    @Test
    @DisplayName("every allowed transition is registered at zero, forbidden and same-status ones are not")
    void registersAllowedTransitionsUpFront() {
        Collection<Counter> counters = registry.find("tasks.status.transitions").counters();

        assertThat(counters).allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(counters)
                .extracting(counter -> counter.getId().getTag("prev_status") + " → " + counter.getId().getTag("new_status"))
                .containsExactlyInAnyOrder(
                        "TO_DO → IN_PROGRESS",
                        "IN_PROGRESS → TO_DO",
                        "IN_PROGRESS → DONE",
                        "DONE → IN_PROGRESS");
    }

    @Test
    @DisplayName("each task operation increments only its own counter")
    void incrementsTaskCounters() {
        metrics.taskCreated();
        metrics.taskCreated();
        metrics.taskReleased();
        metrics.taskDeleted();

        assertThat(registry.get("tasks.creations").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("tasks.released").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("tasks.deleted").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("statusChanged increments only the counter of that transition")
    void incrementsOnlyMatchingTransition() {
        metrics.statusChanged(TaskStatus.TO_DO, TaskStatus.IN_PROGRESS);

        assertThat(registry.get("tasks.status.transitions")
                .tag("prev_status", "TO_DO")
                .tag("new_status", "IN_PROGRESS")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.find("tasks.status.transitions").counters())
                .filteredOn(counter -> counter.count() > 0)
                .hasSize(1);
    }
}
