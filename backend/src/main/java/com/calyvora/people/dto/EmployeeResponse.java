package com.calyvora.people.dto;

import com.calyvora.identity.User;
import com.calyvora.people.Employee;

/** An employee directory entry: identity fields (from User) + HR profile (from Employee). */
public record EmployeeResponse(
        String id,
        String userId,
        String firstName,
        String lastName,
        String email,
        String role,
        String employeeNo,
        String jobTitle,
        String employmentType,
        String employmentStatus,
        String departmentId,
        String managerId,
        String workLocation,
        String phone,
        String startDate
) {
    public static EmployeeResponse of(User user, Employee e) {
        return new EmployeeResponse(
                e.getId().toString(),
                user.getId().toString(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().name(),
                e.getEmployeeNo(),
                e.getJobTitle(),
                e.getEmploymentType() == null ? null : e.getEmploymentType().name(),
                e.getEmploymentStatus().name(),
                e.getDepartmentId() == null ? null : e.getDepartmentId().toString(),
                e.getManagerId() == null ? null : e.getManagerId().toString(),
                e.getWorkLocation(),
                e.getPhone(),
                e.getStartDate() == null ? null : e.getStartDate().toString());
    }
}
