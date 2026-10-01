package com.calyvora.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Fill in a template for one employee (feedback D2: "fill in name → a proper document is generated").
 * {@code overrides} lets the issuer correct or supply any merge field before rendering.
 */
public record GenerateRequest(
        @NotBlank String templateId,
        String employeeId,
        @Size(max = 200) String title,
        Map<String, String> overrides
) {}
