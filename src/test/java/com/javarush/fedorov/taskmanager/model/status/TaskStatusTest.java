package com.javarush.fedorov.taskmanager.model.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskStatus — matrix of allowed transitions")
class TaskStatusTest {

    @ParameterizedTest(name = "{0} → {1} is allowed")
    @CsvSource({
            "TO_DO, IN_PROGRESS",
            "IN_PROGRESS, TO_DO",
            "IN_PROGRESS, DONE",
            "DONE, IN_PROGRESS"
    })
    void allowsExpectedTransitions(TaskStatus from, TaskStatus to) {
        assertThat(from.canTransition(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} is rejected")
    @CsvSource({
            "TO_DO, DONE",
            "DONE, TO_DO"
    })
    void forbidsUnexpectedTransitions(TaskStatus from, TaskStatus to) {
        assertThat(from.canTransition(to)).isFalse();
    }

    @ParameterizedTest(name = "{0} → {0} is allowed (idempotency)")
    @EnumSource(TaskStatus.class)
    void allowsTransitionToSameStatus(TaskStatus status) {
        assertThat(status.canTransition(status)).isTrue();
    }

    @Test
    @DisplayName("every status has transition rules defined")
    void everyStatusHasTransitionRules() {
        for (TaskStatus status : TaskStatus.values()) {
            assertThat(status.canTransition(TaskStatus.IN_PROGRESS))
                    .as("a transition to IN_PROGRESS must be defined from %s", status)
                    .isTrue();
        }
    }
}
