package com.calyvora.access;

/** Whose data a grant reaches: everyone in the company, or the holder's own reporting tree. */
public enum PermissionScope {
    COMPANY,
    TEAM;

    /** The wider of two scopes, for a permission granted twice. */
    public PermissionScope widest(PermissionScope other) {
        return this == COMPANY || other == COMPANY ? COMPANY : TEAM;
    }
}
