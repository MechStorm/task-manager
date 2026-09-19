package com.javarush.fedorov.taskmanager.model.status;

import java.util.Map;
import java.util.Set;

public enum TaskStatus {
    TO_DO,
    IN_PROGRESS,
    DONE;

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            TO_DO, Set.of(IN_PROGRESS),
            IN_PROGRESS, Set.of(TO_DO, DONE),
            DONE, Set.of(IN_PROGRESS)
    );

    public boolean canTransition(TaskStatus target) {
        return this == target || ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}
