package com.calyvora.payroll.dto;

import com.calyvora.payroll.BankFileBuilder;
import com.calyvora.payroll.BankFileFormat;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the bank file would contain, before anybody downloads it.
 *
 * <p>Carries no account numbers. The preview exists so HR can see who is missing details and fix
 * them; showing the numbers would spread unmasked bank details across a screen that does not need
 * them, when the download is the one place they are genuinely required.
 *
 * @param total the sum in the file — deliberately what will actually leave the account, which
 *              differs from the payroll total exactly when somebody has been excluded, i.e. when the
 *              difference matters most
 */
public record BankFilePreviewResponse(
        String format,
        int payable,
        BigDecimal total,
        List<Excluded> excluded
) {
    /** @param reason plain enough to act on — it names the field to fix */
    public record Excluded(String employeeId, String name, String reason) {
    }

    public static BankFilePreviewResponse of(BankFileBuilder.Result result, BankFileFormat format) {
        return new BankFilePreviewResponse(
                format.name(),
                result.included().size(),
                result.total(),
                result.excluded().stream()
                        .map(e -> new Excluded(e.employeeId(), e.name(), e.reason()))
                        .toList());
    }
}
