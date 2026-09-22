package com.calyvora.platform;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Getting a customer's data out, and getting a customer out.
 *
 * <p>The two belong together. A customer who asks to be deleted is usually asking for their data
 * first, and an export that is not known to be complete makes a delete that cannot be taken back
 * unconscionable. Both are therefore driven from the same place — the catalogue — rather than from
 * two hand-written lists of tables that could disagree with each other and with the schema.
 *
 * <p><b>Why the catalogue and not JPA.</b> Enumerating entities would export what the application
 * models, which is not the same as what the customer has. A table written by a migration and read
 * only by a report, a column added to a join table, anything the ORM does not own — all of it is the
 * customer's data and none of it would appear. The catalogue knows what is actually there.
 *
 * <p>Both operations are the platform owner's. A company admin deleting their own workspace is a
 * feature to design deliberately, with a grace period and a way back; what exists here is the
 * vendor's tool for honouring an erasure request and for winding down an account.
 */
@Service
public class TenantDataService {

    private static final Logger log = LoggerFactory.getLogger(TenantDataService.class);

    /**
     * Tables the export skips.
     *
     * <p>{@code refresh_tokens} is live session material, not records — exporting it would hand
     * somebody a file containing usable credentials for every signed-in employee. The rest are the
     * platform's own bookkeeping about the customer rather than the customer's data.
     */
    private static final List<String> NOT_EXPORTED = List.of(
            "refresh_tokens", "email_verification_tokens", "password_reset_codes", "flyway_schema_history");

    private final JdbcTemplate jdbc;
    private final CompanyRepository companyRepository;

    public TenantDataService(JdbcTemplate jdbc, CompanyRepository companyRepository) {
        this.jdbc = jdbc;
        this.companyRepository = companyRepository;
    }

    /** Every table that carries a company_id, from the catalogue rather than a list kept here. */
    private List<String> tenantTables() {
        return jdbc.queryForList(
                "select c.relname from pg_attribute a "
                        + "join pg_class c on c.oid = a.attrelid "
                        + "join pg_namespace n on n.oid = c.relnamespace "
                        + "where a.attname = 'company_id' and c.relkind = 'r' "
                        + "and n.nspname = current_schema() and a.attnum > 0 "
                        + "order by c.relname",
                String.class);
    }

    /**
     * Everything the company owns, as plain JSON-ready maps.
     *
     * <p>Read on the platform owner's connection, which is not bound to the customer's tenant — so
     * the {@code where company_id = ?} in each query is doing the work Row-Level Security would
     * otherwise do. That is the one place in this codebase where the application's own filtering is
     * the whole guarantee, and it is written once here rather than in forty places.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("companyId", companyId.toString());
        meta.put("companyName", company.getName());
        meta.put("exportedAt", java.time.Instant.now().toString());
        out.put("export", meta);

        Map<String, Object> data = new LinkedHashMap<>();
        long rows = 0;
        for (String table : tenantTables()) {
            if (NOT_EXPORTED.contains(table)) {
                continue;
            }
            List<Map<String, Object>> contents = jdbc.queryForList(
                    "select * from " + quoted(table) + " where company_id = ?", companyId);
            rows += contents.size();
            data.put(table, contents);
        }
        // The company row itself carries no company_id, so the sweep above misses it.
        data.put("companies", jdbc.queryForList("select * from companies where id = ?", companyId));
        meta.put("tables", data.size());
        meta.put("rows", rows);
        out.put("data", data);

        log.info("Exported company {} ({}): {} tables, {} rows", companyId, company.getName(),
                data.size(), rows);
        return out;
    }

    /**
     * Delete a customer and everything belonging to them.
     *
     * <p>One statement. V58 made every {@code company_id} foreign key cascade precisely so that this
     * could be one statement: a hand-written list of tables to empty is the wrong instrument, because
     * the day somebody adds a table and forgets the list, an erasure request leaves that table's
     * personal data behind and reports success.
     *
     * <p>The name must be typed back. This is not reversible and there is no grace period — a
     * mis-clicked row in a console listing every customer would otherwise be the end of one of them.
     * Requiring the name means the destructive act cannot be performed by a single gesture, which is
     * the only protection that survives a tired operator.
     */
    @Transactional
    public Map<String, Object> delete(UUID companyId, String confirmationName) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));

        if (confirmationName == null || !company.getName().trim().equalsIgnoreCase(confirmationName.trim())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Type the company's name exactly — \"" + company.getName() + "\" — to confirm deletion.");
        }
        if (company.isPlatform()) {
            // The vendor's own workspace holds the owner account. Deleting it locks everybody out of
            // the console, including whoever would have to undo it.
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "The platform's own workspace cannot be deleted.");
        }

        // Counted before the delete, because afterwards there is nothing left to count. This is the
        // only record that the data existed, so it goes in the log where it outlives the request.
        Map<String, Object> counts = new LinkedHashMap<>();
        long total = 0;
        for (String table : tenantTables()) {
            Long n = jdbc.queryForObject(
                    "select count(*) from " + quoted(table) + " where company_id = ?", Long.class, companyId);
            if (n != null && n > 0) {
                counts.put(table, n);
                total += n;
            }
        }

        // Agencies first, so the failure is a refusal rather than a cascade of surprises: an agency's
        // customers are tenants in their own right (V58 sets their agency_id null rather than
        // deleting them), and the operator should decide what happens to them knowingly.
        List<Map<String, Object>> customers = jdbc.queryForList(
                "select id, name from companies where agency_id = ?", companyId);
        if (!customers.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This agency still has " + customers.size() + " customer "
                            + (customers.size() == 1 ? "company" : "companies")
                            + ". Move or delete them first.");
        }

        log.warn("Deleting company {} ({}) — {} rows across {} tables: {}",
                companyId, company.getName(), total, counts.size(), counts);
        jdbc.update("delete from companies where id = ?", companyId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", companyId.toString());
        result.put("companyName", company.getName());
        result.put("tables", counts.size());
        result.put("rows", total);
        result.put("deleted", counts);
        return result;
    }

    /**
     * A table name from the catalogue, quoted for interpolation.
     *
     * <p>These names come from {@code pg_class} rather than from a request, so this is belt rather
     * than braces — but a table name reaches a SQL string here, and the habit of quoting it is worth
     * more than the argument that this particular one is safe.
     */
    private static String quoted(String table) {
        if (!table.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalStateException("Refusing to interpolate an unexpected table name: " + table);
        }
        return "\"" + table + "\"";
    }
}
