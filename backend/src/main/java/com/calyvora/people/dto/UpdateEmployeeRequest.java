package com.calyvora.people.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        String startDate
) {
}
