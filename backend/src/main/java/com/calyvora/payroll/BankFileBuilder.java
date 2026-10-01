package com.calyvora.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns a month's payroll into the file a bank accepts for a bulk salary transfer.
 *
 * <p>A pure function of (rows, format, month) — no repository, no tenant. The same reasoning as the
 * PF calculator, and again for a stronger reason than tidiness: this file moves money. It has to be
 * checkable by writing down an employee and the line they should produce.
 *
 * <p><b>Validation is the point, not the formatting.</b> Producing a file is twenty lines; refusing
 * to produce a bad one is the part that has value. A row missing an IFSC does not become a payment
 * that fails at the bank after payday has been announced — it is reported back, by name, before the
 * file is downloaded. Nothing is silently dropped, because a quietly shorter file is how somebody
 * goes unpaid for a month and nobody notices until they say so.
 */
public final class BankFileBuilder {

    private BankFileBuilder() {
    }

    /**
     * Indian IFSC: four letters, then a zero, then six alphanumerics. Checked because a malformed one
     * is rejected by the bank for the whole batch, not just that row.
     */
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    /** An employee's pay, as the file needs it. */
    public record Payee(String employeeId, String name, String accountNumber, String ifsc,
                        String bankName, BigDecimal net) {
    }

    /**
     * @param csv       the file itself, empty when nothing could be paid
     * @param included  payees written to the file
     * @param excluded  payees left out, each with the reason, so HR can fix them and re-download
     * @param total     the sum actually in the file — what will leave the account
     */
    public record Result(String csv, List<Payee> included, List<Excluded> excluded, BigDecimal total) {
    }

    /** @param reason plain enough to act on: it is shown to whoever has to fix the record */
    public record Excluded(String employeeId, String name, String reason) {
    }

    public static Result build(List<Payee> payees, BankFileFormat format, String month) {
        List<Payee> included = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Payee p : payees) {
            String problem = problemWith(p);
            if (problem != null) {
                excluded.add(new Excluded(p.employeeId(), p.name(), problem));
                continue;
            }
            included.add(p);
            total = total.add(p.net());
        }

        return new Result(render(included, format, month), included, excluded,
                total.setScale(2, RoundingMode.HALF_UP));
    }

    /** @return why this payee cannot be paid, or null when they can */
    private static String problemWith(Payee p) {
        if (p.net() == null || p.net().signum() <= 0) {
            // Not an error to fix — somebody on unpaid leave for the whole month legitimately nets
            // zero — but it must not become a zero-value transfer the bank rejects.
            return "Nothing payable this month";
        }
        if (isBlank(p.accountNumber())) {
            return "No bank account number on their finance record";
        }
        if (isBlank(p.ifsc())) {
            return "No IFSC on their finance record";
        }
        if (!IFSC.matcher(p.ifsc().trim().toUpperCase()).matches()) {
            return "IFSC \"" + p.ifsc().trim() + "\" is not valid (expected 4 letters, a zero, then 6 characters)";
        }
        if (isBlank(p.name())) {
            return "No name on the employee record";
        }
        return null;
    }

    private static String render(List<Payee> payees, BankFileFormat format, String month) {
        if (payees.isEmpty()) {
            return "";
        }
        String narration = "SALARY " + month;
        StringBuilder out = new StringBuilder();

        switch (format) {
            case HDFC -> {
                out.append("Beneficiary Name,Beneficiary Account Number,IFSC,Amount,Transaction Type,Narration\n");
                for (Payee p : payees) {
                    line(out, p.name(), p.accountNumber(), p.ifsc(), amount(p.net()), "NEFT", narration);
                }
            }
            case ICICI -> {
                out.append("Payee Name,Account Number,IFSC Code,Amount,Payment Mode,Remarks\n");
                for (Payee p : payees) {
                    line(out, p.name(), p.accountNumber(), p.ifsc(), amount(p.net()), "NEFT", narration);
                }
            }
            case AXIS -> {
                out.append("Beneficiary Name,Account No,IFSC,Amount,Mode,Narration,Value Date\n");
                for (Payee p : payees) {
                    line(out, p.name(), p.accountNumber(), p.ifsc(), amount(p.net()), "NEFT", narration,
                            LocalDate.now().toString());
                }
            }
            case GENERIC -> {
                out.append("Employee,Bank,Account Number,IFSC,Amount,Narration\n");
                for (Payee p : payees) {
                    line(out, p.name(), nullToEmpty(p.bankName()), p.accountNumber(), p.ifsc(),
                            amount(p.net()), narration);
                }
            }
        }
        return out.toString();
    }

    /** Whole rupees. Banks reject paise in a bulk salary file, and net pay is already rounded. */
    private static String amount(BigDecimal net) {
        return net.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void line(StringBuilder out, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(cells[i]));
        }
        out.append('\n');
    }

    /**
     * CSV escaping, which matters more here than usual: an unescaped comma in a name shifts every
     * column after it, so an account number lands in the amount field. The bank would either reject
     * the batch or, far worse, pay a number that parsed.
     */
    private static String escape(String value) {
        String v = value == null ? "" : value.trim();
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
