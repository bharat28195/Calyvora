package com.calyvora.document.dto;

import com.calyvora.document.Letterhead;

/**
 * The letterpad as the editor and the preview need it. {@code heading} is already resolved — the
 * client should never have to know that a blank heading means "fall back to the company name".
 */
public record LetterheadResponse(
        String logoUrl,
        String heading,
        String addressLines,
        String footerText,
        String brandColor,
        String fontFamily,
        boolean showDivider,
        String signatureName,
        String signatureTitle,
        /** Whether a letterpad has been uploaded — the bytes are fetched separately. */
        boolean hasBackground,
        boolean useBackground,
        String backgroundName,
        String updatedAt,
        // ---- V73: identity and standard terms ----
        String cin,
        String gstin,
        String website,
        String email,
        String dateStyle,
        Integer probationDays,
        String noticeProbation,
        String noticePeriod,
        String workingDays,
        String workingHours,
        String payDay,
        String jurisdiction,
        // ---- PD-69: page two onwards, and the writing area (millimetres on A4, defaults applied) ----
        /** PAGE2, UPLOADED or DERIVED; null without a letterpad. */
        String continuationSource,
        String laterPages,
        int firstTopMm,
        int firstBottomMm,
        int laterTopMm,
        int laterBottomMm,
        int sideMm
) {
    public static LetterheadResponse of(Letterhead l, String companyName) {
        String heading = l.getHeading() == null || l.getHeading().isBlank() ? companyName : l.getHeading();
        return new LetterheadResponse(l.getLogoUrl(), heading, l.getAddressLines(), l.getFooterText(),
                l.getBrandColor(), l.getFontFamily(), l.isShowDivider(),
                l.getSignatureName(), l.getSignatureTitle(),
                l.getBackgroundName() != null, l.isUseBackground(), l.getBackgroundName(),
                l.getUpdatedAt().toString(),
                l.getCin(), l.getGstin(), l.getWebsite(), l.getEmail(), l.getDateStyle(), l.getProbationDays(),
                l.getNoticeProbation(), l.getNoticePeriod(), l.getWorkingDays(), l.getWorkingHours(),
                l.getPayDay(), l.getJurisdiction(),
                l.getContinuationSource(), l.getLaterPages(),
                or(l.getFirstTopMm(), 40), or(l.getFirstBottomMm(), 32),
                or(l.getLaterTopMm(), 22), or(l.getLaterBottomMm(), 32), or(l.getSideMm(), 22));
    }

    private static int or(Integer v, int fallback) {
        return v == null ? fallback : v;
    }
}
