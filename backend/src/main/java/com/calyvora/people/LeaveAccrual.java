package com.calyvora.people;

/** How an entitlement comes into existence over a year. */
public enum LeaveAccrual {

    /**
     * The whole entitlement exists from 1 January, pro-rated by remaining months for somebody who
     * joins part-way through the year. This is what the product did before policies existed.
     */
    ANNUAL,

    /**
     * Earned per completed month of service — the common Indian arrangement, and the reason people
     * ask "how much have I actually got right now?" rather than reading the annual number.
     */
    MONTHLY
}
