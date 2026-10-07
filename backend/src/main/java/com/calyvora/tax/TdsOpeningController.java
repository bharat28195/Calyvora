package com.calyvora.tax;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.people.EmployeeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Income and TDS from before Orbit this financial year, per employee — a new joiner's previous
 * employer (Form 12B) or the company's old payroll. Whoever manages tax only. See V66.
 */
@RestController
@RequestMapping("/api/v1/payroll/tds-openings")
@PreAuthorize("@perm.has('TAX_MANAGE')")
public class TdsOpeningController {

    private final TdsOpeningBalanceRepository repository;
    private final EmployeeRepository employeeRepository;

    public TdsOpeningController(TdsOpeningBalanceRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    public record OpeningPayload(@NotBlank String coveredThrough,
                                 @NotNull @PositiveOrZero BigDecimal income,
                                 @NotNull @PositiveOrZero BigDecimal tds,
                                 @Size(max = 200) String note) {
    }

    public record OpeningResponse(String employeeId, String financialYear, String coveredThrough,
                                  BigDecimal income, BigDecimal tds, String note) {
        static OpeningResponse of(TdsOpeningBalance o) {
            return new OpeningResponse(o.getEmployeeId().toString(), o.getFinancialYear(), o.getCoveredThrough(),
                    o.getIncome(), o.getTds(), o.getNote());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<OpeningResponse> list(@RequestParam(required = false) String year) {
        return repository.findByCompanyIdAndFinancialYear(TenantContext.getCompanyId(), year(year).label())
                .stream().map(OpeningResponse::of).toList();
    }

    @PutMapping("/{employeeId}")
    @Transactional
    public OpeningResponse save(@PathVariable UUID employeeId, @RequestParam(required = false) String year,
                                @Valid @RequestBody OpeningPayload p) {
        UUID companyId = TenantContext.getCompanyId();
        employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        FinancialYear fy = year(year);
        YearMonth through;
        try {
            through = YearMonth.parse(p.coveredThrough());
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Covered through must look like 2026-09");
        }
        YearMonth first = YearMonth.from(fy.start());
        if (through.isBefore(first) || through.isAfter(first.plusMonths(11))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Covered through must be a month in FY " + fy.label());
        }
        TdsOpeningBalance o = repository.findByCompanyIdAndEmployeeIdAndFinancialYear(companyId, employeeId, fy.label())
                .orElseGet(() -> new TdsOpeningBalance(companyId, employeeId, fy.label()));
        o.setCoveredThrough(through.toString());
        o.setIncome(p.income());
        o.setTds(p.tds());
        o.setNote(p.note() == null || p.note().isBlank() ? null : p.note().trim());
        return OpeningResponse.of(repository.save(o));
    }

    @DeleteMapping("/{employeeId}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID employeeId, @RequestParam(required = false) String year) {
        repository.findByCompanyIdAndEmployeeIdAndFinancialYear(TenantContext.getCompanyId(), employeeId, year(year).label())
                .ifPresent(repository::delete);
        return ResponseEntity.noContent().build();
    }

    private static FinancialYear year(String label) {
        try {
            return label == null || label.isBlank() ? FinancialYear.of(LocalDate.now()) : FinancialYear.parse(label);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Year must look like 2026-27");
        }
    }
}
