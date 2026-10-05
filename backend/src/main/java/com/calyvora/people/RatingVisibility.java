package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.people.dto.EmployeeResponse;

import java.util.List;
import java.util.Set;

/**
 * Who may see whose performance rating.
 *
 * <p>The directory is readable by every member of a company — that's deliberate, it's how people
 * find each other. The rating rode along in the same payload, which meant any employee could read
 * every colleague's score. Salary was already protected; this closes the same gap for ratings.
 *
 * <p>Visible to whoever holds PERFORMANCE_MANAGE (Admin and HR by default, PD-54), to the person
 * themselves, and to that person's manager. The caller passes {@code privileged} in, because this is a
 * static helper and the permission lives in a service.
 */
final class RatingVisibility {

    private RatingVisibility() {}

    static List<EmployeeResponse> filter(List<EmployeeResponse> employees, AuthPrincipal viewer, boolean privileged) {
        if (privileged) {
            return employees;
        }
        String viewerEmployeeId = employees.stream()
                .filter(e -> e.userId().equals(viewer.userId().toString()))
                .map(EmployeeResponse::id)
                .findFirst()
                .orElse(null);
        return employees.stream().map(e -> visible(e, viewer, viewerEmployeeId) ? e : e.withoutRating()).toList();
    }

    static EmployeeResponse filter(EmployeeResponse employee, AuthPrincipal viewer, String viewerEmployeeId,
                                   boolean privileged) {
        return privileged || visible(employee, viewer, viewerEmployeeId)
                ? employee
                : employee.withoutRating();
    }

    private static boolean visible(EmployeeResponse e, AuthPrincipal viewer, String viewerEmployeeId) {
        boolean isSelf = e.userId().equals(viewer.userId().toString());
        boolean isMyReport = viewerEmployeeId != null && viewerEmployeeId.equals(e.managerId());
        return isSelf || isMyReport;
    }
}
