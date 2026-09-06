package com.calyvora.payroll;

/**
 * The layout a bank expects for a bulk salary upload.
 *
 * <p>Every bank publishes its own column order and header rules, and rejects the batch if they are
 * wrong. Modelled as an enum rather than a free-text setting so an unsupported bank fails when the
 * file is requested — with a list of what <em>is</em> supported — rather than at the bank's upload
 * screen after payday has been announced.
 */
public enum BankFileFormat {

    /**
     * A plain, labelled CSV. Not any one bank's format: it is for reading, checking and importing
     * into a spreadsheet, and it is the default because it is the one that cannot be silently wrong.
     */
    GENERIC,

    /** HDFC corporate net banking bulk transfer. */
    HDFC,

    /** ICICI Corporate Internet Banking bulk upload. */
    ICICI,

    /** Axis Corporate Internet Banking bulk upload. */
    AXIS;

    public static BankFileFormat parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERIC;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.calyvora.common.error.ApiException(
                    com.calyvora.common.error.ErrorCode.VALIDATION_ERROR,
                    "Unsupported bank format. Choose one of: GENERIC, HDFC, ICICI, AXIS");
        }
    }
}
