package com.calyvora.people.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Admin edit of any employee's HR profile. All fields optional; nulls leave values unchanged. */
public record UpdateEmployeeRequest(
        @Size(max = 32) String employeeNo,
        @Size(max = 120) String jobTitle,
        @Pattern(regexp = "FULL_TIME|PART_TIME|CONTRACT|INTERN", message = "invalid employment type")
        String employmentType,
        @Pattern(regexp = "ONBOARDING|ACTIVE|TERMINATED", message = "invalid status")
        String employmentStatus,
        String managerId,
        String departmentId,
        @Size(max = 120) String workLocation,
        @Size(max = 40) String phone,
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be YYYY-MM-DD")
        String startDate,
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be YYYY-MM-DD")
        String endDate,
        @Size(max = 30) List<@Size(max = 40) String> skills,
        @Min(0) @Max(5) Integer rating
) {
}
