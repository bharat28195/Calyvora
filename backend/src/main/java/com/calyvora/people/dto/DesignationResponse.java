package com.calyvora.people.dto;

import com.calyvora.people.Designation;

/**
 * A rung on the company's ladder.
 *
 * @param headcount how many people hold it. Shown in the editor so archiving a rung is a decision
 *                  taken with the number in front of you rather than a surprise on the directory.
 */
public record DesignationResponse(
        String id,
        String name,
        int level,
        boolean archived,
        long headcount
) {
    public static DesignationResponse of(Designation d, long headcount) {
        return new DesignationResponse(d.getId().toString(), d.getName(), d.getLevel(), d.isArchived(), headcount);
    }
}
