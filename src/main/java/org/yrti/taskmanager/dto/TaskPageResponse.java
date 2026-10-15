package org.yrti.taskmanager.dto;

import java.util.List;

public record TaskPageResponse(
        List<TaskDTO> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
