package com.calyvora.people;

/** Why a compensation record was created. */
public enum CompensationChangeType {
    INITIAL,     // first salary on record
    HIKE,        // a raise (amount increased)
    ADJUSTMENT,  // a change that isn't a raise (correction, restructure, decrease)
}
