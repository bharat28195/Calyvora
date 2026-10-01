package com.calyvora.invitation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The role agreed when someone is hired, carried on their invitation until they accept it (PD-20).
 *
 * <p>It lives here rather than in an employee row because there is nowhere else for it to live yet:
 * {@code employees.user_id} is mandatory and the user is only created at acceptance. Applied by
 * {@code EmployeeService} the first time the profile is created.
 */
public record HireDetails(String jobTitle, LocalDate startDate, UUID departmentId) {
}
