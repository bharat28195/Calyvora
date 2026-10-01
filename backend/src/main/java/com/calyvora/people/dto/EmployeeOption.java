package com.calyvora.people.dto;

/**
 * One line in a person picker: enough to recognise a colleague, and nothing else.
 *
 * <p>Deliberately not {@link EmployeeResponse}. That record carries phone number, start date,
 * employment status, skills and a performance rating — none of which belongs in an assignee dropdown,
 * and the rating in particular is not directory information. A picker asks "which of these people",
 * so it gets a name, the email that tells two Priyas apart, and the job title that gives the choice
 * context.
 */
public record EmployeeOption(String id, String name, String email, String jobTitle) {}
