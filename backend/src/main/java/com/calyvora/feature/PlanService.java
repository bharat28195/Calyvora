package com.calyvora.feature;

import com.calyvora.billing.PricingService;
import com.calyvora.billing.Subscription;
import com.calyvora.billing.SubscriptionRepository;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.feature.dto.PlanPayload;
import com.calyvora.feature.dto.PlanResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The vendor's catalogue: what each plan includes and costs. */
@Service
public class PlanService {

    private final PlanRepository repository;
    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;

    public PlanService(PlanRepository repository, CompanyRepository companyRepository,
                       SubscriptionRepository subscriptionRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> list() {
        return repository.findAllByOrderBySortOrderAsc().stream().map(PlanResponse::of).toList();
    }

    @Transactional
    public PlanResponse create(PlanPayload payload) {
        String code = requireCode(payload.code());
        if (repository.existsById(code)) {
            throw new ApiException(ErrorCode.CONFLICT, "A plan called " + code + " already exists");
        }
        Plan plan = new Plan(code, requireName(payload.name()));
        applyTo(plan, payload);
        return PlanResponse.of(repository.save(plan));
    }

    @Transactional
    public PlanResponse update(String code, PlanPayload payload) {
        Plan plan = repository.findById(code.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("No plan called " + code));
        if (payload.name() != null) {
            plan.setName(requireName(payload.name()));
        }
        applyTo(plan, payload);
        return PlanResponse.of(repository.save(plan));
    }

    /**
     * Put a company on a plan, or take it off one.
     *
     * <p>Deactivating a plan is preferred to deleting it, and there is no delete: companies point at
     * plan codes, and a deleted plan would leave them pointing at nothing — silently reverting every
     * one of them to feature defaults, which means silently giving them the whole product.
     *
     * @param planCode null takes the company off any plan, back to feature defaults
     */
    @Transactional
    public void assign(UUID companyId, String planCode) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("No such company"));
        if (planCode == null || planCode.isBlank()) {
            company.setPlanCode(null);
            companyRepository.save(company);
            priceFrom(companyId, null);
            return;
        }
        String code = planCode.trim().toUpperCase();
        Plan plan = repository.findById(code)
                .orElseThrow(() -> new NotFoundException("No plan called " + code));
        if (!plan.isActive()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Plan " + code + " is retired. Reactivate it, or choose another.");
        }
        company.setPlanCode(code);
        companyRepository.save(company);
        priceFrom(companyId, plan);
    }

    /**
     * PD-79: what the company pays follows the plan it was put on. Before this, choosing the ₹49 plan
     * changed the modules and left the bill on the ₹149 list, unless the owner also remembered to type
     * an agreed price — the kind of step that gets forgotten on the day it matters.
     *
     * <ul>
     *   <li>A rate agreed by hand is never touched. It is what was signed, and moving a customer
     *       between plans is not a renegotiation.</li>
     *   <li>Otherwise a plan with a price sets that price, copied onto the subscription, so editing the
     *       plan's price later does not re-price months already billed.</li>
     *   <li>A plan with no price, no plan, or a company billed in another currency (plan prices are
     *       rupees) goes back to the published list, if its rate had come from a plan.</li>
     * </ul>
     */
    private void priceFrom(UUID companyId, Plan plan) {
        Subscription sub = subscriptionRepository.findByCompanyId(companyId).orElse(null);
        if (sub == null || sub.isAgreedPrice()) {
            return;
        }
        boolean rupees = PricingService.DEFAULT_CURRENCY.equals(PricingService.currencyOf(sub));
        if (plan != null && plan.getPricePerEmployee() != null && rupees) {
            sub.setPricePerEmployee(plan.getPricePerEmployee());
            sub.setCustomPrice(true);
            sub.setPricePlanCode(plan.getCode());
        } else if (sub.isPlanPrice()) {
            sub.setCustomPrice(false);
            sub.setPricePlanCode(null);
        }
        subscriptionRepository.save(sub);
    }

    private void applyTo(Plan plan, PlanPayload payload) {
        if (payload.description() != null) {
            plan.setDescription(payload.description().isBlank() ? null : payload.description().trim());
        }
        if (payload.pricePerEmployee() != null) {
            if (payload.pricePerEmployee().signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "A price cannot be negative");
            }
            plan.setPricePerEmployee(payload.pricePerEmployee());
        }
        if (payload.sortOrder() != null) {
            plan.setSortOrder(payload.sortOrder());
        }
        if (payload.active() != null) {
            plan.setActive(payload.active());
        }
        if (payload.features() != null) {
            Set<Feature> features = new LinkedHashSet<>();
            for (String raw : payload.features()) {
                try {
                    features.add(Feature.parse(raw));
                } catch (IllegalArgumentException e) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR, "No such feature: " + raw);
                }
            }
            plan.setFeatures(features);
        }
    }

    private String requireCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A plan needs a code");
        }
        String code = raw.trim().toUpperCase();
        if (!code.matches("[A-Z0-9_]{2,32}")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "A plan code is 2–32 characters: letters, digits and underscores");
        }
        return code;
    }

    private String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A plan needs a name");
        }
        return raw.trim();
    }
}
