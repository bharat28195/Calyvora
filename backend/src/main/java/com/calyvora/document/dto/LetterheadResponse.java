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
        String updatedAt
) {
    public static LetterheadResponse of(Letterhead l, String companyName) {
        String heading = l.getHeading() == null || l.getHeading().isBlank() ? companyName : l.getHeading();
        return new LetterheadResponse(l.getLogoUrl(), heading, l.getAddressLines(), l.getFooterText(),
                l.getBrandColor(), l.getFontFamily(), l.isShowDivider(),
                l.getSignatureName(), l.getSignatureTitle(), l.getUpdatedAt().toString());
    }
}
