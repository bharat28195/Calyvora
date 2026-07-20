package com.calyvora.work.dto;

import java.util.List;

/** The board view: the active sprint (null if none) and the tasks currently on the board. */
public record BoardResponse(
        SprintResponse activeSprint,
        List<TaskResponse> tasks
) {
}
