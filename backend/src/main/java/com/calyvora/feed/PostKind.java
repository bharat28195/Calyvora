package com.calyvora.feed;

/** What sort of post this is — drives the icon and accent colour in the feed. */
public enum PostKind {
    UPDATE,
    ANNOUNCEMENT,
    /** Birthdays, work anniversaries, shipped launches. */
    CELEBRATION,
    QUESTION,
}
