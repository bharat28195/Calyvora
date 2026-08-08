package com.calyvora.people.dto;

import com.calyvora.identity.User;
import com.calyvora.people.Employee;

import java.util.List;

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
        String startDate,
        String endDate,
        List<String> skills,
        Integer rating
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
                e.getStartDate() == null ? null : e.getStartDate().toString(),
                e.getEndDate() == null ? null : e.getEndDate().toString(),
                parseSkills(e.getSkills()),
                e.getRating());
    }

    /**
     * The same entry with the performance rating removed — one colleague's rating is not directory
     * information. Applied at the API boundary, where who is asking is known; internal callers
     * (analytics, team overview) keep the full record.
     */
    public EmployeeResponse withoutRating() {
        return rating == null ? this : new EmployeeResponse(id, userId, firstName, lastName, email, role,
                employeeNo, jobTitle, employmentType, employmentStatus, departmentId, managerId,
                workLocation, phone, startDate, endDate, skills, null);
    }

    /** Split the denormalized comma-separated skills column into a list (empty when unset). */
    private static List<String> parseSkills(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split("\\s*,\\s*"));
    }
}
