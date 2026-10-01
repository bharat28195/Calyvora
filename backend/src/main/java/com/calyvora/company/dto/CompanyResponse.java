package com.calyvora.company.dto;

import com.calyvora.company.Company;

public record CompanyResponse(String id, String name, String slug, String status) {

    public static CompanyResponse of(Company company) {
        return new CompanyResponse(company.getId().toString(), company.getName(),
                company.getSlug(), company.getStatus().name());
    }
}
