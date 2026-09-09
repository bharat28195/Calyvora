package com.calyvora.dev;

import com.calyvora.common.security.TenantContext;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.company.CompanyStatus;
import com.calyvora.common.util.Slugs;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.people.AttendanceRecord;
import com.calyvora.people.AttendanceRepository;
import com.calyvora.people.AttendanceStatus;
import com.calyvora.people.Department;
import com.calyvora.people.DepartmentRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.EmploymentStatus;
import com.calyvora.people.EmploymentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a company big enough to be worth measuring.
 *
 * <p>Every functional check so far ran against a seven-person demo, which proves features exist and
 * says nothing about whether they stay usable. The question a 200-seat sale actually turns on is what
 * happens at 200 or 1,000, and the honest way to answer it is to create 1,000 people and time the
 * screens rather than to reason about the code.
 *
 * <p><b>Shaped as a real org, not a flat list.</b> One admin, one HR, then heads → leads → members,
 * four levels deep. A flat company would make every downline either everybody or nobody and would
 * quietly skip the recursive walk in {@code OrgScope} that is the most likely thing to be slow — the
 * app shell asks "do I lead anybody" on every single page load.
 *
 * <p>Its own tenant, so nothing here touches the Northwind demo, and {@link #remove()} deletes it
 * whole. Dev/demo only, like the rest of this package.
 */
@Service
@Profile("!prod")
public class ScaleSeedService {

    private static final Logger log = LoggerFactory.getLogger(ScaleSeedService.class);

    private static final String COMPANY = "Scaleworks Industries";
    private static final String ADMIN_EMAIL = "admin@scaleworks.demo";
    private static final String HR_EMAIL = "hr@scaleworks.demo";
    private static final String PASSWORD = "demopass123";
    private static final String DOMAIN = "@scaleworks.demo";

    /** Inserted in chunks so one flush does not hold ten thousand entities at once. */
    private static final int BATCH = 500;

    private static final String[] FIRST = {
            "Aarav", "Diya", "Vihaan", "Ananya", "Arjun", "Ishita", "Kabir", "Meera", "Rohan", "Sana",
            "Vikram", "Nisha", "Aditya", "Priya", "Karan", "Tara", "Rahul", "Zoya", "Dev", "Anika"};
    private static final String[] LAST = {
            "Sharma", "Nair", "Iyer", "Reddy", "Bose", "Gupta", "Menon", "Rao", "Shah", "Verma",
            "Kulkarni", "Chatterjee", "Pillai", "Joshi", "Desai", "Banerjee", "Malhotra", "Sinha"};
    private static final String[] TEAMS = {
            "Engineering", "Sales", "Support", "Finance", "Operations", "Marketing", "Legal", "IT"};

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public ScaleSeedService(CompanyRepository companyRepository, UserRepository userRepository,
                            EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                            AttendanceRepository attendanceRepository, PasswordEncoder passwordEncoder,
                            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @param headcount        people to create, including the admin and HR.
     * @param attendanceDays   days of attendance history per person. This is the row count that grows
     *                         fastest — 1,000 people over 14 days is 14,000 rows — and it is what makes
     *                         the team and attendance screens do real work rather than read an empty
     *                         table.
     */
    public record ScaleResult(String company, String adminEmail, String headEmail, String password,
                              int headcount, int departments, int attendanceRows,
                              int maxDownline, long millis) {}

    @Transactional
    public ScaleResult seed(int headcount, int attendanceDays) {
        long started = System.currentTimeMillis();
        int people = Math.max(10, Math.min(headcount, 5000));
        int days = Math.max(0, Math.min(attendanceDays, 60));

        Optional<User> existing = userRepository.findByEmail(ADMIN_EMAIL);
        if (existing.isPresent()) {
            UUID companyId = existing.get().getCompanyId();
            TenantContext.setCompanyId(companyId);
            try {
                long count = employeeRepository.findByCompanyId(companyId).size();
                return new ScaleResult(COMPANY, ADMIN_EMAIL, headEmail(0), PASSWORD, (int) count,
                        departmentRepository.findByCompanyIdOrderByName(companyId).size(), -1, -1,
                        System.currentTimeMillis() - started);
            } finally {
                TenantContext.clear();
            }
        }

        Company company = new Company(UUID.randomUUID(), COMPANY, Slugs.slugify(COMPANY), CompanyStatus.ACTIVE);
        companyRepository.save(company);
        UUID companyId = company.getId();

        TenantContext.setCompanyId(companyId);
        try {
            List<Department> departments = new ArrayList<>();
            for (String name : TEAMS) {
                departments.add(new Department(UUID.randomUUID(), companyId, name));
            }
            departmentRepository.saveAll(departments);

            // The shape. Heads scale with headcount so the tree stays realistic rather than becoming
            // one enormous fan-out: ~8 heads, five leads each, everybody else spread beneath the leads.
            int heads = Math.max(2, Math.min(8, people / 120));
            int leadsPerHead = 5;
            int leads = heads * leadsPerHead;

            String encoded = passwordEncoder.encode(PASSWORD);
            List<User> users = new ArrayList<>(people);
            List<Employee> employees = new ArrayList<>(people);

            UUID adminId = UUID.randomUUID();
            users.add(user(adminId, companyId, ADMIN_EMAIL, "Ada", "Sen", Role.ADMIN, encoded));
            UUID hrId = UUID.randomUUID();
            users.add(user(hrId, companyId, HR_EMAIL, "Hari", "Menon", Role.HR, encoded));

            List<UUID> headUserIds = new ArrayList<>();
            for (int i = 0; i < heads; i++) {
                UUID id = UUID.randomUUID();
                headUserIds.add(id);
                users.add(user(id, companyId, headEmail(i), FIRST[i % FIRST.length],
                        LAST[i % LAST.length], Role.MANAGER, encoded));
            }
            List<UUID> leadUserIds = new ArrayList<>();
            for (int i = 0; i < leads; i++) {
                UUID id = UUID.randomUUID();
                leadUserIds.add(id);
                // MEMBER on purpose. A lead in this product is somebody with reports, not somebody with
                // a role — if these were MANAGERs the test would prove the easy case only.
                users.add(user(id, companyId, "lead" + i + DOMAIN, FIRST[(i + 3) % FIRST.length],
                        LAST[(i + 5) % LAST.length], Role.MEMBER, encoded));
            }
            int rest = people - users.size();
            List<UUID> memberUserIds = new ArrayList<>(Math.max(rest, 0));
            for (int i = 0; i < rest; i++) {
                UUID id = UUID.randomUUID();
                memberUserIds.add(id);
                users.add(user(id, companyId, "emp" + i + DOMAIN, FIRST[i % FIRST.length],
                        LAST[(i / FIRST.length) % LAST.length], Role.MEMBER, encoded));
            }
            saveInBatches(users, userRepository::saveAll);

            // Employee rows, then the reporting lines wired on a second pass — a manager_id has to
            // point at an employee id, which does not exist until the row does.
            java.util.Map<UUID, UUID> employeeIdByUser = new java.util.HashMap<>();
            for (User u : users) {
                UUID empId = UUID.randomUUID();
                employeeIdByUser.put(u.getId(), empId);
                Employee e = new Employee(empId, companyId, u.getId());
                e.setEmploymentStatus(EmploymentStatus.ACTIVE);
                e.setEmploymentType(EmploymentType.FULL_TIME);
                e.setStartDate(LocalDate.now().minusDays(200 + (empId.hashCode() & 0x3FF)));
                employees.add(e);
            }
            wireTree(employeeIdByUser, adminId, hrId, headUserIds, leadUserIds, memberUserIds,
                    employees, departments);
            saveInBatches(employees, employeeRepository::saveAll);

            int attendanceRows = seedAttendance(companyId, employees, days, adminId);

            int maxDownline = rest / Math.max(leads, 1) * leadsPerHead + leadsPerHead;
            long millis = System.currentTimeMillis() - started;
            log.info("Scale seed: {} people, {} attendance rows, {} ms", users.size(), attendanceRows, millis);
            return new ScaleResult(COMPANY, ADMIN_EMAIL, headEmail(0), PASSWORD, users.size(),
                    departments.size(), attendanceRows, maxDownline, millis);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Deletes the whole tenant, discovering what to delete rather than listing it.
     *
     * <p>An explicit table list was tried first and is the wrong tool: there are more than forty
     * tenant-scoped tables, they reference each other, and two attempts hit a different foreign key
     * each time — {@code employees_manager_id_fkey} because employees reference employees, then
     * {@code refresh_tokens_user_id_fkey} because merely logging in had created rows the list did not
     * know about. A hand-maintained order is stale the day somebody adds a table, and this has to run
     * against a real database where leaving a thousand undeletable rows behind is the bad outcome.
     *
     * <p>So: find every table with a {@code company_id}, then delete in repeated passes, each
     * statement inside a savepoint. A delete that fails on a foreign key is rolled back to its
     * savepoint and retried on the next pass, by which time its children are gone. The loop ends when
     * a pass deletes nothing, which either means everything is gone or that no order exists — and the
     * second case reports what is left rather than pretending success.
     */
    @Transactional
    public boolean remove() {
        Optional<User> admin = userRepository.findByEmail(ADMIN_EMAIL);
        if (admin.isEmpty()) {
            return false;
        }
        UUID companyId = admin.get().getCompanyId();
        TenantContext.setCompanyId(companyId);
        try {
            // The reporting tree is a self-reference, so flatten it before anything else — no ordering
            // of DELETEs can satisfy a constraint a row has against its own table.
            List<Employee> employees = employeeRepository.findByCompanyId(companyId);
            for (Employee e : employees) {
                e.setManagerId(null);
            }
            employeeRepository.saveAll(employees);
            employeeRepository.flush();

            List<String> tables = jdbc.queryForList(
                    "select table_name from information_schema.columns "
                            + "where table_schema = 'public' and column_name = 'company_id' "
                            + "and table_name <> 'companies'", String.class);

            // Keyed on user_id rather than company_id, so it never appears in the query above, and it
            // is created by the simple act of logging in.
            jdbc.update("delete from refresh_tokens where user_id in (select id from users where company_id = ?)",
                    companyId);

            List<String> remaining = new ArrayList<>(tables);
            boolean progress = true;
            while (progress && !remaining.isEmpty()) {
                progress = false;
                for (java.util.Iterator<String> it = remaining.iterator(); it.hasNext(); ) {
                    String table = it.next();
                    if (deleteQuietly(table, companyId)) {
                        it.remove();
                        progress = true;
                    }
                }
            }
            if (!remaining.isEmpty()) {
                log.warn("Scale teardown left rows in {} — delete them by hand or add the missing order",
                        remaining);
                return false;
            }
            jdbc.update("delete from users where company_id = ?", companyId);
            jdbc.update("delete from companies where id = ?", companyId);
            return true;
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * One tenant-scoped delete, inside its own savepoint.
     *
     * <p>Postgres aborts the whole transaction on any error, so a failed delete without a savepoint
     * would take the successful ones with it. The savepoint is what makes "try, and retry next pass"
     * possible at all.
     */
    private boolean deleteQuietly(String table, UUID companyId) {
        // Savepoints taken on the JDBC connection directly, not through the transaction manager:
        // JpaTransactionManager refuses them outright ("JpaDialect does not support savepoints")
        // unless nested transactions are enabled globally, and turning that on across the whole
        // application to tidy up a dev fixture would be a poor trade.
        return Boolean.TRUE.equals(jdbc.execute((java.sql.Connection c) -> {
            java.sql.Savepoint savepoint = c.setSavepoint();
            try (java.sql.PreparedStatement ps =
                         c.prepareStatement("delete from " + table + " where company_id = ?")) {
                ps.setObject(1, companyId);
                ps.executeUpdate();
                c.releaseSavepoint(savepoint);
                return true;
            } catch (java.sql.SQLException e) {
                c.rollback(savepoint);
                return false;
            }
        }));
    }

    // --- helpers ---------------------------------------------------------------

    private void wireTree(java.util.Map<UUID, UUID> employeeIdByUser, UUID adminId, UUID hrId,
                          List<UUID> headUserIds, List<UUID> leadUserIds, List<UUID> memberUserIds,
                          List<Employee> employees, List<Department> departments) {
        java.util.Map<UUID, Employee> byId = new java.util.HashMap<>();
        for (Employee e : employees) {
            byId.put(e.getId(), e);
        }
        UUID adminEmp = employeeIdByUser.get(adminId);
        set(byId, employeeIdByUser.get(hrId), adminEmp, departments.get(3).getId());
        for (int i = 0; i < headUserIds.size(); i++) {
            set(byId, employeeIdByUser.get(headUserIds.get(i)), adminEmp,
                    departments.get(i % departments.size()).getId());
        }
        for (int i = 0; i < leadUserIds.size(); i++) {
            UUID head = employeeIdByUser.get(headUserIds.get(i % headUserIds.size()));
            set(byId, employeeIdByUser.get(leadUserIds.get(i)), head,
                    departments.get(i % departments.size()).getId());
        }
        for (int i = 0; i < memberUserIds.size(); i++) {
            UUID lead = employeeIdByUser.get(leadUserIds.get(i % leadUserIds.size()));
            set(byId, employeeIdByUser.get(memberUserIds.get(i)), lead,
                    departments.get(i % departments.size()).getId());
        }
    }

    private static void set(java.util.Map<UUID, Employee> byId, UUID employeeId, UUID managerId, UUID deptId) {
        Employee e = byId.get(employeeId);
        if (e != null) {
            e.setManagerId(managerId);
            e.setDepartmentId(deptId);
        }
    }

    /**
     * Attendance for the recent window, weekends left out.
     *
     * <p>This is the row count that grows fastest and the reason the team screens do real work: they
     * read a month of it for every person on the roster. A mix of statuses rather than all PRESENT, so
     * the counts on the page are not all identical and a grouping bug would be visible.
     */
    private int seedAttendance(UUID companyId, List<Employee> employees, int days, UUID markedBy) {
        if (days == 0) {
            return 0;
        }
        List<AttendanceRecord> batch = new ArrayList<>(BATCH);
        int written = 0;
        LocalDate today = LocalDate.now();
        for (int d = 0; d < days; d++) {
            LocalDate date = today.minusDays(d);
            if (date.getDayOfWeek().getValue() >= 6) {
                continue;
            }
            int i = 0;
            for (Employee e : employees) {
                AttendanceStatus status = switch ((i + d) % 10) {
                    case 7 -> AttendanceStatus.WORK_FROM_HOME;
                    case 8 -> AttendanceStatus.ON_LEAVE;
                    case 9 -> AttendanceStatus.ABSENT;
                    default -> AttendanceStatus.PRESENT;
                };
                batch.add(new AttendanceRecord(UUID.randomUUID(), companyId, e.getId(), date, status, markedBy));
                i++;
                if (batch.size() >= BATCH) {
                    attendanceRepository.saveAll(batch);
                    written += batch.size();
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            attendanceRepository.saveAll(batch);
            written += batch.size();
        }
        return written;
    }

    private static <T> void saveInBatches(List<T> all, java.util.function.Consumer<List<T>> save) {
        for (int i = 0; i < all.size(); i += BATCH) {
            save.accept(all.subList(i, Math.min(i + BATCH, all.size())));
        }
    }

    private User user(UUID id, UUID companyId, String email, String first, String last,
                      Role role, String encodedPassword) {
        User u = new User(id, companyId, email, first, last, role, UserStatus.ACTIVE);
        u.setPasswordHash(encodedPassword);
        u.setEmailVerifiedAt(Instant.now());
        return u;
    }

    private static String headEmail(int i) {
        return "head" + i + DOMAIN;
    }
}
