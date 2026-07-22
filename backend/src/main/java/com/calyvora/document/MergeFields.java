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

    /** Format a date the way a letter should read ("4 March 2026"). */
    public static String date(LocalDate d) {
        return d == null ? null : LONG_DATE.format(d);
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
                new Field("salary.annual", "Annual compensation"),
                new Field("salary.monthly", "Monthly compensation"),
                new Field("salary.currency", "Currency"),
                new Field("salary.effectiveDate", "Compensation effective from"),
                new Field("company.name", "Company name"),
                new Field("today", "Today's date"),
                new Field("signatory.name", "Signed by (you)"),
                new Field("signatory.title", "Signatory's title"));
    }

    /** A merge field offered in the editor. */
    public record Field(String key, String label) {}
}
