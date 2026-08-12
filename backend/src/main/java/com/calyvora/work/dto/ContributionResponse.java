package com.calyvora.work.dto;

import java.util.List;

/**
 * What one employee actually delivered inside a date window — the Work OS half of the review
 * evidence pack (PD-17, D1).
 *
 * <p>Deliberately a flat, already-rendered view: refs, titles and links are resolved here so the
 * Performance module can compose an evidence pack without reaching into Work's entities. That keeps
 * the dependency one-way (performance → work) and shaped like {@code WorkItemResponse}.
 */
public record ContributionResponse(
        List<Item> tasks,
        List<Item> tickets,
        List<SprintItem> sprints,
        int tasksDelivered,
        int ticketsResolved,
        int storyPointsDelivered) {

    /** One delivered thing, ready to render and link to. */
    public record Item(
            String ref,
            String title,
            String projectId,
            String projectName,
            String link,
            String finishedOn,
            Integer storyPoints) {
    }

    /** A sprint this person delivered into, with their share of it — not the sprint's totals. */
    public record SprintItem(
            String sprintId,
            String name,
            String projectName,
            String startDate,
            String endDate,
            String status,
            int itemsDelivered,
            int pointsDelivered) {
    }

    public static ContributionResponse empty() {
        return new ContributionResponse(List.of(), List.of(), List.of(), 0, 0, 0);
    }
}
