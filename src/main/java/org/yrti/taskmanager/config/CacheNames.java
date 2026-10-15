package org.yrti.taskmanager.config;

import java.util.Set;

final class CacheNames {

    static final Set<String> ALL = Set.of("tasks", "task", "taskByStatus", "taskByCategory", "tasksByOwner");

    private CacheNames() {
    }
}
