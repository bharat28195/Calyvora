package com.calyvora.recruit.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Turn a candidate into a colleague (PD-20): invite them, agree their role, and raise the joining
 * letter — one action instead of four screens.
 */
public record HireRequest(
        @Pattern(regexp = "ADMIN|HR|MANAGER|MEMBER", message = "Choose Admin, HR, Manager or Member")
        String role,

        @Size(max = 120, message = "Job title cannot be longer than 120 characters")
        String jobTitle,

        LocalDate startDate,

        UUID departmentId,

        /** Raise the joining letter now. Defaults to true. */
        Boolean issueJoiningLetter
) {
    public HireRequest {
        jobTitle = jobTitle == null || jobTitle.isBlank() ? null : jobTitle.trim();
        role = role == null || role.isBlank() ? "MEMBER" : role.trim().toUpperCase(java.util.Locale.ENGLISH);
    }

    public boolean shouldIssueJoiningLetter() {
        return issueJoiningLetter == null || issueJoiningLetter;
    }
}
