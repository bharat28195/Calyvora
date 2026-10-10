package com.calyvora.document;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code {{merge.field}}} engine behind document generation (feedback D2).
 *
 * <p>Deliberately tiny and pure — no expressions, no logic, just named substitution. A field with no
 * value renders as {@code —} rather than an empty gap or a leftover {@code {{token}}}, so a letter is
 * never issued with visible plumbing in it.
 */
public final class MergeFields {

    /** {@code {{ field.name }}} — whitespace tolerated inside the braces. */
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    /** Shown when a field has no value. */
    public static final String EMPTY = "—";

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private MergeFields() {
    }

    /** Replace every known token; unknown or valueless tokens collapse to {@link #EMPTY}. */
    public static String render(String body, Map<String, String> values) {
        if (body == null || body.isBlank()) {
            return "";
        }
        Matcher m = TOKEN.matcher(body);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(
                    value == null || value.isBlank() ? EMPTY : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The distinct field names a template body references, in first-seen order. */
    public static List<String> placeholdersIn(String body) {
        Set<String> found = new LinkedHashSet<>();
        if (body != null) {
            Matcher m = TOKEN.matcher(body);
            while (m.find()) {
                found.add(m.group(1));
            }
        }
        return new ArrayList<>(found);
    }

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter NUMERIC_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    /** Format a date the way a letter should read ("4 March 2026"). */
    public static String date(LocalDate d) {
        return d == null ? null : LONG_DATE.format(d);
    }

    /**
     * A date in the company's chosen style: LONG "4 March 2026", SHORT "04 Aug, 2026" (the usual
     * Indian letter style), NUMERIC "04/08/2026".
     */
    public static String date(LocalDate d, String style) {
        if (d == null) return null;
        if ("SHORT".equals(style)) return SHORT_DATE.format(d);
        if ("NUMERIC".equals(style)) return NUMERIC_DATE.format(d);
        return LONG_DATE.format(d);
    }

    /**
     * An amount the way an Indian letter prints it: lakh and crore grouping, always two decimals —
     * 2768832 becomes "27,68,832.00". Western grouping ("2,768,832") reads as a mistake to anyone who
     * signs Indian salary letters.
     */
    public static String inr(java.math.BigDecimal amount) {
        if (amount == null) return null;
        java.math.BigDecimal v = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        boolean negative = v.signum() < 0;
        String plain = v.abs().toPlainString();
        String whole = plain.substring(0, plain.indexOf('.'));
        String paise = plain.substring(plain.indexOf('.'));
        StringBuilder out = new StringBuilder();
        int n = whole.length();
        if (n <= 3) {
            out.append(whole);
        } else {
            String head = whole.substring(0, n - 3);
            String tail = whole.substring(n - 3);
            StringBuilder grouped = new StringBuilder();
            for (int i = head.length(); i > 0; i -= 2) {
                grouped.insert(0, head.substring(Math.max(0, i - 2), i));
                if (i - 2 > 0) grouped.insert(0, ',');
            }
            out.append(grouped).append(',').append(tail);
        }
        return (negative ? "-" : "") + out + paise;
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    /**
     * Whole rupees in words, Indian style: 2768832 → "Twenty Seven Lakh Sixty Eight Thousand Eight
     * Hundred and Thirty Two". Paise are ignored. Null for null.
     */
    public static String inWords(java.math.BigDecimal amount) {
        if (amount == null) return null;
        long n = amount.abs().setScale(0, java.math.RoundingMode.DOWN).longValueExact();
        if (n == 0) return "Zero";
        StringBuilder out = new StringBuilder();
        long crore = n / 10_000_000; n %= 10_000_000;
        long lakh = n / 100_000; n %= 100_000;
        long thousand = n / 1000; n %= 1000;
        if (crore > 0) out.append(inWords(java.math.BigDecimal.valueOf(crore))).append(" Crore ");
        if (lakh > 0) out.append(belowHundred((int) lakh)).append(" Lakh ");
        if (thousand > 0) out.append(belowHundred((int) thousand)).append(" Thousand ");
        if (n > 0) out.append(belowThousand((int) n));
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    private static String belowHundred(int n) {
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
    }

    private static String belowThousand(int n) {
        int h = n / 100, rest = n % 100;
        if (h == 0) return belowHundred(rest);
        return ONES[h] + " Hundred" + (rest == 0 ? "" : " and " + belowHundred(rest));
    }

    /** Human tenure between two dates ("2 years 3 months"); null when it can't be computed. */
    public static String tenure(LocalDate from, LocalDate to) {
        if (from == null) {
            return null;
        }
        LocalDate end = to == null ? LocalDate.now() : to;
        if (end.isBefore(from)) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(from, end);
        long years = months / 12;
        long rest = months % 12;
        if (years == 0 && rest == 0) {
            long days = ChronoUnit.DAYS.between(from, end);
            return days + (days == 1 ? " day" : " days");
        }
        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append(years == 1 ? " year" : " years");
        if (rest > 0) sb.append(sb.length() > 0 ? " " : "").append(rest).append(rest == 1 ? " month" : " months");
        return sb.toString();
    }

    /**
     * The catalogue shown in the editor so authors know what they can insert.
     * Ordered by how often a letter needs them.
     */
    public static List<Field> catalogue() {
        return List.of(
                new Field("employee.fullName", "Full name"),
                new Field("employee.firstName", "First name"),
                new Field("employee.lastName", "Last name"),
                new Field("employee.email", "Work email"),
                new Field("employee.employeeNo", "Employee ID"),
                new Field("employee.jobTitle", "Job title"),
                new Field("employee.department", "Department"),
                new Field("employee.manager", "Reporting manager"),
                new Field("employee.employmentType", "Employment type"),
                new Field("employee.workLocation", "Work location"),
                new Field("employee.phone", "Phone"),
                new Field("employee.startDate", "Start date"),
                new Field("employee.endDate", "Last working day"),
                new Field("employee.tenure", "Tenure (computed)"),
                new Field("employee.probationEndDate", "Probation ends (computed)"),
                new Field("salary.annual", "Annual gross salary"),
                new Field("salary.annualInWords", "Annual gross, in words"),
                new Field("salary.monthly", "Monthly gross salary"),
                new Field("salary.ctc", "Annual CTC (gross + employer contributions)"),
                new Field("salary.ctcInWords", "Annual CTC, in words"),
                new Field("salary.currency", "Currency"),
                new Field("salary.effectiveDate", "Compensation effective from"),
                new Field("salary.previousAnnual", "Previous annual gross"),
                new Field("salary.increase", "Increase (amount)"),
                new Field("salary.increasePercent", "Increase (%)"),
                new Field("salary.structure", "Salary structure table — earnings + employer contributions = CTC"),
                new Field("salary.takeHome", "Salary structure table — earnings − deductions = take-home"),
                new Field("payslips.recent", "Last three payslips (table) — for salary certificates"),
                new Field("company.name", "Company name"),
                new Field("company.address", "Company address"),
                new Field("company.cin", "CIN"),
                new Field("company.gstin", "GSTIN"),
                new Field("company.website", "Website"),
                new Field("company.email", "Company email"),
                new Field("terms.probationDays", "Probation (days)"),
                new Field("terms.noticeProbation", "Notice during probation"),
                new Field("terms.noticePeriod", "Notice period"),
                new Field("terms.workingDays", "Working days"),
                new Field("terms.workingHours", "Working hours"),
                new Field("terms.payDay", "Salary paid by"),
                new Field("terms.jurisdiction", "Courts of (jurisdiction)"),
                new Field("leave.summary", "Leave entitlement (from leave policies)"),
                new Field("today", "Today's date"),
                new Field("signatory.name", "Signed by"),
                new Field("signatory.title", "Signatory's title"),
                // Asked for when a letter needs them — nothing in the profile can know these.
                new Field("letter.reason", "Reason"),
                new Field("letter.details", "Details"),
                new Field("letter.effectiveDate", "Effective date"),
                new Field("letter.responseDays", "Days to respond"),
                new Field("letter.meetingDate", "Meeting date"),
                new Field("letter.purpose", "Purpose (e.g. visa application)"),
                new Field("letter.addressedTo", "Addressed to"),
                new Field("letter.residentialAddress", "Residential address"),
                new Field("transfer.fromLocation", "Transferring from"),
                new Field("transfer.toLocation", "Transferring to"),
                new Field("transfer.reportingTo", "New reporting manager"),
                new Field("probation.extendedUntil", "Probation extended until"),
                new Field("probation.extensionMonths", "Extension (months)"),
                new Field("pip.startDate", "PIP starts"),
                new Field("pip.endDate", "PIP ends"),
                new Field("pip.goals", "PIP goals"),
                new Field("bonus.amount", "Bonus amount"),
                new Field("bonus.period", "Bonus for (period)"),
                new Field("bonus.payoutDate", "Bonus paid with (month)"),
                new Field("intern.stipend", "Monthly stipend"),
                new Field("intern.duration", "Internship duration"),
                new Field("intern.mentor", "Mentor"),
                new Field("intern.project", "Project / area"),
                new Field("resignation.date", "Resignation received on"),
                new Field("fnf.salaryDue", "F&F: salary for days worked"),
                new Field("fnf.leaveEncashment", "F&F: leave encashment"),
                new Field("fnf.gratuity", "F&F: gratuity"),
                new Field("fnf.bonus", "F&F: bonus / other dues"),
                new Field("fnf.recoveries", "F&F: recoveries (notice shortfall, assets, advances)"),
                new Field("fnf.netPayable", "F&F: net payable"),
                new Field("fnf.paymentDate", "F&F: paid on"));
    }

    /** A merge field offered in the editor. */
    public record Field(String key, String label) {}
}
