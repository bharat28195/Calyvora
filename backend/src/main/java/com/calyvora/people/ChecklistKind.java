package com.calyvora.people;

/**
 * Which checklist a task belongs to (PD-20). Joining and leaving are the same machinery — a list of
 * things somebody has to do, in order, ticked off — so they share a table rather than duplicating
 * one. What differs is who may tick: an onboarding item is the new joiner's own, an exit item is
 * clearance and belongs to their manager.
 */
public enum ChecklistKind {
    ONBOARDING,
    EXIT,
}
