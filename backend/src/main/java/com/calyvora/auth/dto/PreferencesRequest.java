package com.calyvora.auth.dto;

/**
 * A person's own display preferences. The whole set is saved each time; a blank field means
 * "use the default" (the company's language, the company's clock, the language's date style).
 */
public record PreferencesRequest(String language, String timezone, String dateFormat, String timeFormat) {
}
