package com.calyvora.work.dto;

/**
 * A task an employee is working on, for their People profile ("what he/she is working on" — feedback C4).
 * {@code overdue} flags a past-due open task (the "delay vs advance" signal).
 */
public record WorkItemResponse(
        String ref,
        String title,
        String status,
        String priority,
        String projectId,
        String projectName,
        String dueDate,
        boolean overdue
) {}
