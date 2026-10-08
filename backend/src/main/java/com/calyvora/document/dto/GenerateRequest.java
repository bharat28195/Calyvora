package com.calyvora.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Fill in a template for one employee (feedback D2: "fill in name → a proper document is generated").
 * {@code overrides} lets the issuer correct or supply any merge field before rendering — for somebody
 * who is not an employee yet (an offer letter), they are the whole of it. {@code body}, when given, is
 * the letter as the issuer edited it by hand, and is issued exactly as written.
 */
public record GenerateRequest(
        @NotBlank String templateId,
        String employeeId,
        @Size(max = 200) String title,
        Map<String, String> overrides,
        @Size(max = 100_000) String body
) {
    public GenerateRequest(String templateId, String employeeId, String title, Map<String, String> overrides) {
        this(templateId, employeeId, title, overrides, null);
    }
}
