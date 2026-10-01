package com.calyvora.people.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleOnboardingTaskRequest(@NotNull Boolean completed) {
}
