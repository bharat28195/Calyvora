package com.calyvora.email;

/**
 * Everything the vendor needs in the "someone wants a trial" email, so the decision can be taken from
 * the phone without opening the console. {@code consoleUrl} is where to go to act on it.
 */
public record TrialEnquiry(
        String companyName,
        String contactName,
        String email,
        String phone,
        String teamSize,
        String note,
        String source,
        String consoleUrl
) {
}
