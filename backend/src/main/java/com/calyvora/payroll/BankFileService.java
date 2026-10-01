package com.calyvora.payroll;

import com.calyvora.people.CompensationService;
import com.calyvora.people.EmployeeFinance;
import com.calyvora.people.EmployeeFinanceService;
import com.calyvora.people.dto.PayrollRunResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The bulk salary transfer file for a month's payroll.
 *
 * <p>Built on the server and streamed as a download, and it has to be: account numbers are masked in
 * every API response ({@code bankAccountMasked}) so the full value never reaches the browser. A file
 * assembled in the frontend could not contain the account numbers it needs, and making it able to
 * would undo that.
 */
@Service
public class BankFileService {

    private final CompensationService compensationService;
    private final EmployeeFinanceService financeService;

    public BankFileService(CompensationService compensationService, EmployeeFinanceService financeService) {
        this.compensationService = compensationService;
        this.financeService = financeService;
    }

    /**
     * What the file would contain, and who is missing from it.
     *
     * <p>The same computation as the download, exposed separately so a screen can show the exclusions
     * <em>before</em> anyone uploads anything to a bank. Fixing a missing IFSC after the batch has
     * been rejected costs a re-run of payday; fixing it here costs a minute.
     */
    @Transactional
    public BankFileBuilder.Result preview(String month, BankFileFormat format) {
        PayrollRunResponse run = compensationService.payrollRun(month);
        List<BankFileBuilder.Payee> payees = new ArrayList<>();
        for (PayrollRunResponse.Row row : run.rows()) {
            EmployeeFinance finance = financeService.rawOrNull(UUID.fromString(row.employeeId()));
            payees.add(new BankFileBuilder.Payee(
                    row.employeeId(),
                    // The bank matches on the account, but the beneficiary name is what a human reads
                    // on the statement. Prefer the name on the bank record when there is one — it is
                    // the one the account is actually held in, which may not be the display name.
                    finance != null && notBlank(finance.getBankAccountName())
                            ? finance.getBankAccountName() : row.name(),
                    finance == null ? null : finance.getBankAccountNo(),
                    finance == null ? null : finance.getBankIfsc(),
                    finance == null ? null : finance.getBankName(),
                    row.net()));
        }
        return BankFileBuilder.build(payees, format, run.month());
    }

    /** A filename somebody can find again in their downloads folder three weeks later. */
    public String fileNameFor(String month, BankFileFormat format) {
        return "salary-" + (month == null || month.isBlank() ? "current" : month)
                + "-" + format.name().toLowerCase() + ".csv";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
