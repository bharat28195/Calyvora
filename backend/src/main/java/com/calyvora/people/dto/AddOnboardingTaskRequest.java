package com.calyvora.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddOnboardingTaskRequest(@NotBlank @Size(max = 200) String title) {
}
