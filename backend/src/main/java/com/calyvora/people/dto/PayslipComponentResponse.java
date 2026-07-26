package com.calyvora.people.dto;

import com.calyvora.people.PayslipComponent;

import java.math.BigDecimal;

/** A payslip-template component as returned to the editor. */
public record PayslipComponentResponse(
        String id,
        String name,
        String kind,
        String calc,
        BigDecimal value,
        boolean basis,
        int sortOrder
) {
    public static PayslipComponentResponse of(PayslipComponent c) {
        return new PayslipComponentResponse(c.getId().toString(), c.getName(), c.getKind().name(),
                c.getCalc().name(), c.getValue(), c.isBasis(), c.getSortOrder());
    }
}
