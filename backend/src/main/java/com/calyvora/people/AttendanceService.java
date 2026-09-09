package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.dto.AttendanceDayResponse;
import com.calyvora.people.dto.AttendanceEntryResponse;
import com.calyvora.people.dto.AttendanceMonthResponse;
import com.calyvora.people.dto.MarkAttendanceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Daily attendance (feedback C.4 â€” phase 2 of the "attendance option"). Phase 1 derived present vs
 * on-leave from approved leave; this stores a real row per employee per day.
 *
 * <p>Two rules keep it from becoming double data entry:
 * <ul>
 *   <li><b>Approved leave fills the day automatically.</b> Nobody marks time off twice â€” an
 *       unmarked day covered by approved leave resolves to {@code ON_LEAVE} (flagged {@code derived}).</li>
 *   <li><b>A marked row always wins</b> over anything derived, so a correction sticks.</li>
 * </ul>
 * Company holidays fill the day the same way; weekends resolve to {@code WEEK_OFF}. The work-week
 * itself is still hardcoded Monâ€“Fri â€” configurable work-week policy is the remaining debt here.
 */
@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final DepartmentRepository departmentRepository;
    private final HolidayRepository holidayRepository;
    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRepository;
    private final com.calyvora.company.CompanySettingsRepository companySettingsRepository;

    public AttendanceService(AttendanceRepository attendanceRepository, EmployeeRepository employeeRepository,
                             EmployeeService employeeService, DepartmentRepository departmentRepository,
                             HolidayRepository holidayRepository,
                             UserRepository userRepository, LeaveRequestRepository leaveRepository,
                             com.calyvora.company.CompanySettingsRepository companySettingsRepository) {
        this.holidayRepository = holidayRepository;
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.leaveRepository = leaveRepository;
        this.companySettingsRepository = companySettingsRepository;
    }

    /**
     * "Now" as the company experiences it. Attendance is the one place where the server's own clock is
     * the wrong clock: on a UTC host, an Indian employee clocking in at 04:15 IST was recorded at 22:45
     * on the <em>previous</em> day, putting the punch on the wrong date and the wrong payslip.
     */
    private java.time.ZoneId zone() {
        return companySettingsRepository.findById(TenantContext.getCompanyId())
                .map(com.calyvora.company.CompanySettings::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .map(tz -> {
                    try {
                        return java.time.ZoneId.of(tz);
                    } catch (RuntimeException badZone) {
                        return java.time.ZoneOffset.UTC;
                    }
                })
                .orElse(java.time.ZoneOffset.UTC);
    }

    /** Today's date in the company's timezone. */
    private LocalDate today() {
        return LocalDate.now(zone());
    }

    /** The current wall-clock time in the company's timezone, to the minute. */
    private LocalTime nowTime() {
        return LocalTime.now(zone()).withSecond(0).withNano(0);
    }

    // ---- team day sheet ----

    /**
     * Every employee's status for one day. Owner/Admin (enforced in the controller).
     *
     * <p>Not {@code readOnly}: asking People for the directory may provision missing profiles, and a
     * read-only transaction would swallow those inserts without flushing them.
     */
    @Transactional
    public AttendanceDayResponse day(LocalDate date) {
        UUID companyId = TenantContext.getCompanyId();
        // Profiles are provisioned lazily by People; ask for the directory first so a company that
        // has never opened it still gets a full day sheet instead of an empty one.
        employeeService.directory();
        List<Employee> employees = employeeRepository.findByCompanyId(companyId);
        Map<UUID, User> users = usersById(companyId);
        Map<UUID, AttendanceRecord> marked = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findByCompanyIdAndDate(companyId, date)) {
            marked.put(r.getEmployeeId(), r);
        }
        Map<UUID, List<LeaveRequest>> leave = approvedLeaveByEmployee(companyId);

        List<AttendanceEntryResponse> entries = new ArrayList<>();
        long present = 0, onLeave = 0, absent = 0, unmarked = 0;
        for (Employee e : employees) {
            AttendanceEntryResponse entry = resolve(e, users.get(e.getUserId()), date,
                    Optional.ofNullable(marked.get(e.getId())), leave.getOrDefault(e.getId(), List.of()));
            entries.add(entry);
            if (entry.status() == null) {
                unmarked++;
            } else {
                AttendanceStatus s = AttendanceStatus.valueOf(entry.status());
                if (s.isWorking()) present++;
                else if (s == AttendanceStatus.ON_LEAVE) onLeave++;
                else if (s == AttendanceStatus.ABSENT) absent++;
            }
        }
        entries.sort((a, b) -> a.employeeName().compareToIgnoreCase(b.employeeName()));
        return new AttendanceDayResponse(date.toString(), employees.size(), present, onLeave, absent,
                unmarked, entries);
    }

    // ---- one employee's month ----

    /**
     * One month for every employee in the company, in a fixed number of queries.
     *
     * <p>Exists for the payroll run, which needs everybody's attendance to work out loss of pay.
     * Calling {@link #month} per person costs three queries each — employee, user, records — so two
     * hundred people is six hundred round trips to the database, and that was most of the seven
     * seconds a two-hundred-person run took.
     *
     * <p>The arithmetic is not reimplemented here. It walks the same days and calls the same
     * {@link #resolve} as {@link #month}, so a holiday, a week-off or an approved leave is decided in
     * exactly one place. A second copy of that logic would drift, and it would drift silently on
     * payslips — the one document where being quietly wrong matters most.
     */
    @Transactional(readOnly = true)
    public Map<UUID, AttendanceMonthResponse> monthForEveryone(YearMonth month, java.util.Set<UUID> only) {
        UUID companyId = TenantContext.getCompanyId();
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        // The company's holidays for the month, once. resolve() otherwise queries them per DAY per
        // EMPLOYEE — thirty thousand queries for a thousand people, which is the whole cost of this.
        Map<LocalDate, Holiday> holidays = new HashMap<>();
        for (Holiday h : holidayRepository.findByCompanyIdAndDateBetweenOrderByDateAsc(companyId, from, to)) {
            holidays.putIfAbsent(h.getDate(), h);
        }

        // Only the people the caller actually needs. A payroll run skips anybody without a salary, and
        // computing a month for them is work whose result is thrown away — at a thousand employees
        // with no compensation on record that was the entire request.
        List<Employee> employees = employeeRepository.findByCompanyId(companyId).stream()
                .filter(e -> only == null || only.contains(e.getId()))
                .toList();
        Map<UUID, User> usersById = usersById(companyId);
        Map<UUID, List<LeaveRequest>> leaveByEmployee = approvedLeaveByEmployee(companyId);

        Map<UUID, Map<LocalDate, AttendanceRecord>> markedByEmployee = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findByCompanyIdAndDateBetween(companyId, from, to)) {
            markedByEmployee.computeIfAbsent(r.getEmployeeId(), k -> new HashMap<>()).put(r.getDate(), r);
        }

        Map<UUID, AttendanceMonthResponse> out = new HashMap<>();
        for (Employee e : employees) {
            out.put(e.getId(), buildMonth(e, usersById.get(e.getUserId()), month,
                    markedByEmployee.getOrDefault(e.getId(), Map.of()),
                    leaveByEmployee.getOrDefault(e.getId(), List.of()), holidays));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public AttendanceMonthResponse month(UUID employeeId, YearMonth month) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);

        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<LocalDate, AttendanceRecord> marked = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository
                .findByEmployeeIdAndDateBetweenOrderByDateAsc(employeeId, from, to)) {
            marked.put(r.getDate(), r);
        }
        List<LeaveRequest> leave = approvedLeaveByEmployee(companyId).getOrDefault(employeeId, List.of());
        return buildMonth(employee, user, month, marked, leave, null);
    }

    /**
     * Walks a month for one person and totals it.
     *
     * <p>The one implementation, shared by {@link #month} and {@link #monthForEveryone}: the two differ
     * only in how they fetch, never in what they conclude. Keeping the arithmetic here is what stops a
     * payroll run and a payslip disagreeing about the same person's loss of pay — the sort of
     * discrepancy nobody finds until an employee does.
     */
    private AttendanceMonthResponse buildMonth(Employee employee, User user, YearMonth month,
                                               Map<LocalDate, AttendanceRecord> marked,
                                               List<LeaveRequest> leave,
                                               Map<LocalDate, Holiday> holidays) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<AttendanceEntryResponse> days = new ArrayList<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AttendanceStatus s : AttendanceStatus.values()) {
            counts.put(s.name(), 0L);
        }
        double worked = 0;
        long expected = 0;
        LocalDate today = today();

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceEntryResponse entry = resolve(employee, user, d,
                    Optional.ofNullable(marked.get(d)), leave, holidays);
            days.add(entry);
            if (entry.status() == null) {
                continue;
            }
            AttendanceStatus s = AttendanceStatus.valueOf(entry.status());
            counts.merge(s.name(), 1L, Long::sum);
            // Only days that have already happened and were expected count toward the rate.
            if (!s.isNonWorkingDay() && !d.isAfter(today)) {
                expected++;
                if (s == AttendanceStatus.HALF_DAY) worked += 0.5;
                else if (s.isWorking()) worked += 1;
            }
        }

        String name = user == null ? "Employee" : (user.getFirstName() + " " + user.getLastName()).trim();
        Double rate = expected == 0 ? null : Math.round(worked * 1000.0 / expected) / 10.0;
        return new AttendanceMonthResponse(employee.getId().toString(), name, month.toString(), days, counts,
                Math.round(worked * 10.0) / 10.0, expected, rate);
    }

    // ---- marking ----

    /** Owner/Admin marks (or corrects) any employee's day. Upsert on (employee, date). */
    @Transactional
    public AttendanceEntryResponse mark(MarkAttendanceRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeRepository
                .findByIdAndCompanyId(UUID.fromString(req.employeeId()), companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        LocalDate date = req.date() == null || req.date().isBlank() ? today() : LocalDate.parse(req.date());
        if (date.isAfter(today())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Attendance can't be marked for a future date");
        }

        AttendanceRecord record = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), date)
                .orElseGet(() -> attendanceRepository.save(new AttendanceRecord(UUID.randomUUID(), companyId,
                        employee.getId(), date, AttendanceStatus.valueOf(req.status()), principal.userId())));
        record.setStatus(AttendanceStatus.valueOf(req.status()));
        record.setMarkedBy(principal.userId());
        if (req.checkIn() != null) record.setCheckIn(parseTime(req.checkIn()));
        if (req.checkOut() != null) record.setCheckOut(parseTime(req.checkOut()));
        if (req.note() != null) record.setNote(req.note().isBlank() ? null : req.note().trim());
        validateTimes(record);

        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return of(employee, user, date, record, false);
    }

    /** The signed-in employee clocks in for today. Idempotent â€” a second call won't move the time. */
    @Transactional
    public AttendanceEntryResponse checkIn(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        LocalDate today = today();
        AttendanceRecord record = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today)
                .orElseGet(() -> attendanceRepository.save(new AttendanceRecord(UUID.randomUUID(), companyId,
                        employee.getId(), today, AttendanceStatus.PRESENT, null)));
        if (record.getCheckIn() == null) {
            record.setCheckIn(nowTime());
            // Someone clocking in is present, unless an admin deliberately marked the day otherwise.
            if (record.getStatus() == AttendanceStatus.ABSENT) {
                record.setStatus(AttendanceStatus.PRESENT);
            }
        }
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return of(employee, user, today, record, false);
    }

    /** The signed-in employee clocks out. Later calls overwrite â€” leaving twice means the later one. */
    @Transactional
    public AttendanceEntryResponse checkOut(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        LocalDate today = today();
        AttendanceRecord record = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Check in first"));
        record.setCheckOut(nowTime());
        validateTimes(record);
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return of(employee, user, today, record, false);
    }

    /** Clear today's clock-in/out for the signed-in employee, so the day is open again. */
    @Transactional
    public AttendanceEntryResponse clearToday(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        LocalDate today = today();
        attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today)
                .ifPresent(attendanceRepository::delete);
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return resolve(employee, user, today, Optional.empty(),
                approvedLeaveByEmployee(companyId).getOrDefault(employee.getId(), List.of()));
    }

    /** Today's row for the signed-in employee (null status when they haven't clocked in). */
    @Transactional(readOnly = true)
    public AttendanceEntryResponse today(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        LocalDate today = today();
        return resolve(employee, user, today,
                attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today),
                approvedLeaveByEmployee(companyId).getOrDefault(employee.getId(), List.of()));
    }

    /** The employee behind the signed-in user, for `/me` endpoints. */
    @Transactional(readOnly = true)
    public UUID myEmployeeId(AuthPrincipal principal) {
        return requireSelf(TenantContext.getCompanyId(), principal).getId();
    }

    // ---- resolution ----

    /**
     * A day's status: the marked row if there is one, else approved leave, else a weekend, else
     * unmarked (null status) â€” which the UI shows as "not marked yet" rather than inventing a value.
     */
    private AttendanceEntryResponse resolve(Employee employee, User user, LocalDate date,
                                            Optional<AttendanceRecord> marked, List<LeaveRequest> leave) {
        return resolve(employee, user, date, marked, leave, null);
    }

    /**
     * As above, with the company's holidays for the period already in hand.
     *
     * <p>Null means "look it up", which is what every single-day caller does. A map means the caller is
     * walking many days for many people and has fetched them once.
     */
    private AttendanceEntryResponse resolve(Employee employee, User user, LocalDate date,
                                            Optional<AttendanceRecord> marked, List<LeaveRequest> leave,
                                            Map<LocalDate, Holiday> prefetchedHolidays) {
        if (marked.isPresent()) {
            return of(employee, user, date, marked.get(), false);
        }
        for (LeaveRequest lr : leave) {
            if (!date.isBefore(lr.getStartDate()) && !date.isAfter(lr.getEndDate())) {
                return entry(employee, user, date, AttendanceStatus.ON_LEAVE.name(), null, null,
                        lr.getType().name().toLowerCase() + (lr.getReason() == null ? "" : " Â· " + lr.getReason()),
                        true);
            }
        }
        // A company holiday closes the day for everyone (unless someone marked otherwise above).
        //
        // Read from a prefetched map when the caller has one. This lookup used to be a query PER DAY
        // PER EMPLOYEE — a month for one person was thirty queries, and a payroll run over a thousand
        // people was thirty thousand. It is the single most expensive thing in this class and it was
        // invisible, because at seven employees thirty queries a head is unnoticeable.
        Optional<Holiday> holiday = (prefetchedHolidays != null
                ? Optional.ofNullable(prefetchedHolidays.get(date))
                : holidayRepository
                        .findByCompanyIdAndDateBetweenOrderByDateAsc(employee.getCompanyId(), date, date).stream()
                        .findFirst())
                .filter(h -> !h.isOptional());
        if (holiday.isPresent()) {
            return entry(employee, user, date, AttendanceStatus.HOLIDAY.name(), null, null,
                    holiday.get().getName(), true);
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return entry(employee, user, date, AttendanceStatus.WEEK_OFF.name(), null, null, null, true);
        }
        return entry(employee, user, date, null, null, null, null, true);
    }

    private AttendanceEntryResponse of(Employee employee, User user, LocalDate date,
                                       AttendanceRecord r, boolean derived) {
        return entry(employee, user, date, r.getStatus().name(),
                r.getCheckIn() == null ? null : r.getCheckIn().toString(),
                r.getCheckOut() == null ? null : r.getCheckOut().toString(),
                r.getNote(), derived);
    }

    private AttendanceEntryResponse entry(Employee employee, User user, LocalDate date, String status,
                                          String checkIn, String checkOut, String note, boolean derived) {
        String name = user == null ? "Employee" : (user.getFirstName() + " " + user.getLastName()).trim();
        return new AttendanceEntryResponse(employee.getId().toString(), name, employee.getJobTitle(),
                departmentName(employee), date.toString(), status, checkIn, checkOut, note, derived);
    }

    /**
     * Department name, for the "who's out in this team" breakdown. A company has a handful of
     * departments and the lookup is by primary key, so this stays cheap without a cache that would
     * have to be tenant-scoped to be safe.
     */
    private String departmentName(Employee employee) {
        if (employee.getDepartmentId() == null) {
            return null;
        }
        return departmentRepository.findByIdAndCompanyId(employee.getDepartmentId(), employee.getCompanyId())
                .map(Department::getName).orElse(null);
    }

    // ---- helpers ----

    private Map<UUID, List<LeaveRequest>> approvedLeaveByEmployee(UUID companyId) {
        Map<UUID, List<LeaveRequest>> byEmployee = new HashMap<>();
        for (LeaveRequest lr : leaveRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            if (lr.getStatus() == LeaveStatus.APPROVED) {
                byEmployee.computeIfAbsent(lr.getEmployeeId(), k -> new ArrayList<>()).add(lr);
            }
        }
        return byEmployee;
    }

    private Map<UUID, User> usersById(UUID companyId) {
        Map<UUID, User> users = new HashMap<>();
        for (User u : userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId)) {
            users.put(u.getId(), u);
        }
        return users;
    }

    /**
     * The employee behind the signed-in user. Profiles are auto-provisioned by People, so we go
     * through {@link EmployeeService#ensureEmployeeId} rather than failing for a user who simply
     * hasn't been listed in the directory yet.
     */
    private Employee requireSelf(UUID companyId, AuthPrincipal principal) {
        UUID employeeId = employeeService.ensureEmployeeId(companyId, principal.userId());
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("No employee profile for this user"));
    }

    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Time must look like 09:30");
        }
    }

    private static void validateTimes(AttendanceRecord r) {
        if (r.getCheckIn() != null && r.getCheckOut() != null && r.getCheckOut().isBefore(r.getCheckIn())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Check-out can't be before check-in");
        }
    }
}
