package com.calyvora.feed;

/** Who can see a post. Kept deliberately small — more options means more mistakes. */
public enum PostVisibility {
    /** Everyone in the company. */
    COMPANY,
    /** One department only. */
    DEPARTMENT,
}
