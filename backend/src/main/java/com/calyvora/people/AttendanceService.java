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
    private final AttendancePunchRepository punchRepository;
    private final com.calyvora.shift.ShiftRepository shiftRepository;
    private final com.calyvora.shift.ShiftAssignmentRepository assignmentRepository;

    public AttendanceService(AttendanceRepository attendanceRepository, EmployeeRepository employeeRepository,
                             EmployeeService employeeService, DepartmentRepository departmentRepository,
                             HolidayRepository holidayRepository,
                             UserRepository userRepository, LeaveRequestRepository leaveRepository,
                             com.calyvora.company.CompanySettingsRepository companySettingsRepository,
                             AttendancePunchRepository punchRepository,
                             com.calyvora.shift.ShiftRepository shiftRepository,
                             com.calyvora.shift.ShiftAssignmentRepository assignmentRepository) {
        this.punchRepository = punchRepository;
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
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
     * "Now" as this person experiences it. Attendance is the one place where the server's own clock
     * is the wrong clock: on a UTC host, an Indian employee clocking in at 04:15 IST was recorded at
     * 22:45 on the <em>previous</em> day, putting the punch on the wrong date and the wrong payslip.
     *
     * <p>Per employee, not per company. A Bengaluru company with a designer in Berlin was recording
     * her 09:00 arrival as 12:30, and every "late" on her month was the clock's doing rather than
     * hers. The chain — her zone, else the company's, else the default — lives in {@link Timezones}
     * so that the /me response, which the screens set their clocks from, resolves it identically.
     *
     * <p>This used to fall back to UTC for a company with no settings row. That is a company that
     * never opened its settings page, and it should behave exactly like one that saved the defaults.
     */
    private java.time.ZoneId zoneFor(Employee employee) {
        return Timezones.resolve(employee,
                companySettingsRepository.findById(TenantContext.getCompanyId()).orElse(null));
    }

    /** Today's date where this person is. */
    private LocalDate today(Employee employee) {
        return LocalDate.now(zoneFor(employee));
    }

    /** The current wall-clock time where this person is, to the minute. */
    private LocalTime nowTime(Employee employee) {
        return LocalTime.now(zoneFor(employee)).withSecond(0).withNano(0);
    }

    // ---- team day sheet ----

    /**
     * Every employee's status for one day. Owner/Admin (enforced in the controller).
     *
     * <p>It was slow for four reasons and all four were the same reason: <em>company-wide data
     * fetched more than once</em>. The company's people were loaded three times over (directory, then
     * employees, then users). Every leave request the company had ever filed was read to answer a
     * question about one day. And the department name — six possible answers — was looked up once per
     * employee.
     *
     * <p>{@code readOnly} since profiles stopped being provisioned on the read path. That is not a
     * cosmetic annotation here: without it Hibernate dirty-checks every one of the thousand entities
     * this method loads, at flush, to discover that a GET changed nothing.
     */
    @Transactional(readOnly = true)
    public AttendanceDayResponse day(LocalDate date) {
        UUID companyId = TenantContext.getCompanyId();
        // One load of users and one of employees, and no response objects built for a list we are not
        // returning. Nothing is provisioned: profiles are created with their user now.
        EmployeeService.Roster roster = employeeService.rosterForRead();
        List<Employee> employees = roster.employees();
        Map<UUID, User> users = new HashMap<>();
        for (User u : roster.users()) {
            users.put(u.getId(), u);
        }
        Map<UUID, AttendanceRecord> marked = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findByCompanyIdAndDate(companyId, date)) {
            marked.put(r.getEmployeeId(), r);
        }
        Map<UUID, List<LeaveRequest>> leave = approvedLeaveByEmployee(companyId, date, date);

        // Is this ONE day a holiday: asked once, not once per employee. resolve() looks it up itself
        // when given nothing, which on a day sheet meant a query per person — a thousand queries to
        // answer a question with a single answer for the whole company.
        Map<LocalDate, Holiday> holidays = new HashMap<>();
        for (Holiday h : holidayRepository.findByCompanyIdAndDateBetweenOrderByDateAsc(companyId, date, date)) {
            holidays.putIfAbsent(h.getDate(), h);
        }
        Prefetch prefetch = new Prefetch(holidays, departmentNames(companyId), workFacts(companyId, date, date));

        List<AttendanceEntryResponse> entries = new ArrayList<>();
        long present = 0, onLeave = 0, absent = 0, unmarked = 0;
        for (Employee e : employees) {
            AttendanceEntryResponse entry = resolve(e, users.get(e.getUserId()), date,
                    Optional.ofNullable(marked.get(e.getId())), leave.getOrDefault(e.getId(), List.of()),
                    prefetch);
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
        if (only != null && only.isEmpty()) {
            return new HashMap<>();
        }

        // The company's holidays for the month, once. resolve() otherwise queries them per DAY per
        // EMPLOYEE — thirty thousand queries for a thousand people, which is the whole cost of this.
        Map<LocalDate, Holiday> holidays = new HashMap<>();
        for (Holiday h : holidayRepository.findByCompanyIdAndDateBetweenOrderByDateAsc(companyId, from, to)) {
            holidays.putIfAbsent(h.getDate(), h);
        }

        // Only the people the caller actually needs. A payroll run skips anybody without a salary, and
        // computing a month for them is work whose result is thrown away — at a thousand employees
        // with no compensation on record that was the entire request.
        // A named group (a lead's team) is read by id: the people, their accounts and their leave,
        // never the company's — a team of fifty out of a thousand must cost a team of fifty.
        List<Employee> employees = only == null
                ? employeeRepository.findByCompanyId(companyId)
                : employeeRepository.findByCompanyIdAndIdIn(companyId, only);
        Map<UUID, User> usersById;
        if (only == null) {
            usersById = usersById(companyId);
        } else {
            usersById = new HashMap<>();
            for (User u : userRepository.findAllById(employees.stream().map(Employee::getUserId).toList())) {
                usersById.put(u.getId(), u);
            }
        }
        Map<UUID, List<LeaveRequest>> leaveByEmployee;
        if (only == null) {
            leaveByEmployee = approvedLeaveByEmployee(companyId, from, to);
        } else {
            leaveByEmployee = new HashMap<>();
            for (LeaveRequest lr : leaveRepository.findByCompanyIdAndEmployeeIdInAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                    companyId, only, LeaveStatus.APPROVED, to, from)) {
                if (!lr.getStartDate().isAfter(to) && !lr.getEndDate().isBefore(from)) {
                    leaveByEmployee.computeIfAbsent(lr.getEmployeeId(), k -> new ArrayList<>()).add(lr);
                }
            }
        }
        // A named group (a lead's team) is filtered in the database: reading the company's month to
        // draw a team of ten out of a thousand is the cost the team screens were rewritten to avoid.
        WorkFacts work = only == null ? workFacts(companyId, from, to)
                : workFacts(companyId, punchRepository.findByCompanyIdAndEmployeeIdInAndDateBetween(companyId, only, from, to),
                        assignmentRepository.slotsFor(companyId, only, from, to));
        Prefetch prefetch = new Prefetch(holidays, departmentNames(companyId), work);

        Map<UUID, Map<LocalDate, AttendanceRecord>> markedByEmployee = new HashMap<>();
        List<AttendanceRecord> records = only == null
                ? attendanceRepository.findByCompanyIdAndDateBetween(companyId, from, to)
                : attendanceRepository.findByCompanyIdAndEmployeeIdInAndDateBetween(companyId, only, from, to);
        for (AttendanceRecord r : records) {
            markedByEmployee.computeIfAbsent(r.getEmployeeId(), k -> new HashMap<>()).put(r.getDate(), r);
        }

        Map<UUID, AttendanceMonthResponse> out = new HashMap<>();
        for (Employee e : employees) {
            out.put(e.getId(), buildMonth(e, usersById.get(e.getUserId()), month,
                    markedByEmployee.getOrDefault(e.getId(), Map.of()),
                    leaveByEmployee.getOrDefault(e.getId(), List.of()), prefetch));
        }
        return out;
    }

    /**
     * A month shaped for a calendar: how many people were present, on leave, absent or unmarked on
     * each day, plus whether the day was a holiday and what it was called.
     *
     * <p>Built on {@link #monthForEveryone}, so a day in this grid and the same day on the day
     * sheet cannot disagree — they resolve through the same {@code resolve()}. The alternative, a
     * query per day, is thirty round trips to draw one screen.
     *
     * @param only the employees to count, or null for everyone in the company
     */
    @Transactional(readOnly = true)
    public com.calyvora.people.dto.AttendanceMonthSummaryResponse monthSummary(YearMonth month,
                                                                               java.util.Set<UUID> only) {
        UUID companyId = TenantContext.getCompanyId();
        Map<UUID, AttendanceMonthResponse> everyone = monthForEveryone(month, only);

        Map<LocalDate, Holiday> holidays = new HashMap<>();
        for (Holiday h : holidayRepository.findByCompanyIdAndDateBetweenOrderByDateAsc(
                companyId, month.atDay(1), month.atEndOfMonth())) {
            holidays.putIfAbsent(h.getDate(), h);
        }

        // date -> counts, walked once per person rather than once per day: the per-person months are
        // already in hand and re-scanning them thirty times would be the same work thirty times over.
        Map<LocalDate, long[]> tally = new java.util.TreeMap<>();
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            tally.put(d, new long[5]);   // present, onLeave, absent, unmarked, weekOff
        }
        for (AttendanceMonthResponse person : everyone.values()) {
            for (AttendanceEntryResponse entry : person.days()) {
                long[] row = tally.get(LocalDate.parse(entry.date()));
                if (row == null) {
                    continue;
                }
                if (entry.status() == null) {
                    row[3]++;
                    continue;
                }
                AttendanceStatus s = AttendanceStatus.valueOf(entry.status());
                if (s.isWorking()) row[0]++;
                else if (s == AttendanceStatus.ON_LEAVE) row[1]++;
                else if (s == AttendanceStatus.ABSENT) row[2]++;
                else row[4]++;   // holiday or week-off: not a working day for that person
            }
        }

        List<com.calyvora.people.dto.AttendanceMonthSummaryResponse.Day> days = new ArrayList<>();
        for (var e : tally.entrySet()) {
            Holiday h = holidays.get(e.getKey());
            long[] row = e.getValue();
            days.add(new com.calyvora.people.dto.AttendanceMonthSummaryResponse.Day(
                    e.getKey().toString(), row[0], row[1], row[2], row[3], row[4],
                    h != null, h == null ? null : h.getName()));
        }
        return new com.calyvora.people.dto.AttendanceMonthSummaryResponse(
                month.toString(), everyone.size(), days);
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
        List<LeaveRequest> leave = approvedLeaveFor(employeeId, from, to);
        // One person is still thirty days, and this walk asks for both the holiday and the department
        // on every one of them. Passing null here would have been a query per day for each.
        Map<LocalDate, Holiday> holidays = new HashMap<>();
        for (Holiday h : holidayRepository.findByCompanyIdAndDateBetweenOrderByDateAsc(companyId, from, to)) {
            holidays.putIfAbsent(h.getDate(), h);
        }
        return buildMonth(employee, user, month, marked, leave,
                new Prefetch(holidays, departmentNames(companyId), workFactsFor(employee, from, to)));
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
                                               Prefetch prefetch) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<AttendanceEntryResponse> days = new ArrayList<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AttendanceStatus s : AttendanceStatus.values()) {
            counts.put(s.name(), 0L);
        }
        double worked = 0;
        long expected = 0;
        long notRecorded = 0;
        LocalDate today = today(employee);

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceEntryResponse entry = resolve(employee, user, d,
                    Optional.ofNullable(marked.get(d)), leave, prefetch);
            days.add(entry);

            if (entry.status() == null) {
                // A null status out of resolve() means one specific thing: a working day — not a
                // weekend, not a holiday, not covered by leave — with nothing recorded on it.
                //
                // This used to skip out here, so those days reached neither total and the rate was
                // worked days over *days that happened to have a row*. Somebody who logged in three
                // times in a month read 3/3 and 100%. The denominator has to be the days they were
                // expected, or the number measures nothing.
                //
                // Today is left out while it is still empty: the day is not over, and counting it as
                // missed would show everybody a dip each morning that repaired itself on check-in.
                if (d.isBefore(today)) {
                    expected++;
                    notRecorded++;
                }
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
                Math.round(worked * 10.0) / 10.0, expected, notRecorded, rate);
    }

    // ---- marking ----

    /** Owner/Admin marks (or corrects) any employee's day. Upsert on (employee, date). */
    @Transactional
    public AttendanceEntryResponse mark(MarkAttendanceRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = employeeRepository
                .findByIdAndCompanyId(UUID.fromString(req.employeeId()), companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        LocalDate date = req.date() == null || req.date().isBlank() ? today(employee) : LocalDate.parse(req.date());
        AttendanceRecord record = writeDay(employee, date, AttendanceStatus.valueOf(req.status()),
                req.checkIn(), req.checkOut(), req.note(), principal.userId());
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return of(employee, user, date, record, false, null);
    }

    /**
     * A manager or HR corrects many days at once — several people, several dates, one status — and
     * it applies straight away. They hold the right to change attendance, so routing their own change
     * through an approval queue would only make them approve themselves. The reason is required and
     * kept on every day it touched. The caller has already checked whose days these may be.
     */
    @Transactional
    public List<AttendanceEntryResponse> correctMany(List<UUID> employeeIds, List<LocalDate> dates,
                                                     AttendanceStatus status, String checkIn, String checkOut,
                                                     String reason, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Give a reason for the correction");
        }
        Map<UUID, User> users = usersById(companyId);
        List<AttendanceEntryResponse> out = new ArrayList<>();
        for (UUID employeeId : new java.util.LinkedHashSet<>(employeeIds)) {
            Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                    .orElseThrow(() -> new NotFoundException("Employee not found"));
            for (LocalDate date : new java.util.TreeSet<>(dates)) {
                AttendanceRecord record = writeDay(employee, date, status, checkIn, checkOut, reason, principal.userId());
                out.add(of(employee, users.get(employee.getUserId()), date, record, false, null));
            }
        }
        return out;
    }

    /**
     * Sets one person's day by hand: the single place a manual change is written, so a mark, a bulk
     * correction and an approved regularization cannot disagree about what a correction does.
     * {@code markedBy} is what exempts the day from the automatic absent and half-day rules.
     */
    AttendanceRecord writeDay(Employee employee, LocalDate date, AttendanceStatus status,
                              String checkIn, String checkOut, String note, UUID markedBy) {
        UUID companyId = employee.getCompanyId();
        if (date.isAfter(today(employee))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Attendance can't be marked for a future date");
        }
        AttendanceRecord record = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), date)
                .orElseGet(() -> attendanceRepository.save(new AttendanceRecord(UUID.randomUUID(), companyId,
                        employee.getId(), date, status, markedBy)));
        record.setStatus(status);
        record.setMarkedBy(markedBy);
        if (checkIn != null) record.setCheckIn(parseTime(checkIn));
        if (checkOut != null) record.setCheckOut(parseTime(checkOut));
        if (note != null) record.setNote(note.isBlank() ? null : note.trim());
        validateTimes(record);
        // Typing the day's times states the whole day: it becomes one session, replacing whatever
        // check-ins were recorded, so effective hours follow what was entered.
        if (checkIn != null || checkOut != null) {
            punchRepository.deleteByEmployeeIdAndDate(employee.getId(), date);
            if (record.getCheckIn() != null) {
                AttendancePunch session = new AttendancePunch(UUID.randomUUID(), companyId, employee.getId(),
                        date, record.getCheckIn());
                session.setCheckOut(record.getCheckOut());
                punchRepository.save(session);
            }
        }
        return record;
    }

    /**
     * The signed-in employee checks in. Each check-in after a check-out opens a new session (V70), so
     * a lunch break is recorded as the gap between two sessions rather than counted as work. While a
     * session is open a second call does nothing: it never moves the time.
     */
    @Transactional
    public AttendanceEntryResponse checkIn(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        LocalDate today = today(employee);
        AttendanceRecord record = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today)
                .orElseGet(() -> attendanceRepository.save(new AttendanceRecord(UUID.randomUUID(), companyId,
                        employee.getId(), today, AttendanceStatus.PRESENT, null)));
        List<AttendancePunch> sessions = punchRepository.findByEmployeeIdAndDateOrderByCheckInAsc(employee.getId(), today);
        boolean open = !sessions.isEmpty() && sessions.get(sessions.size() - 1).getCheckOut() == null;
        if (!open) {
            LocalTime now = nowTime(employee);
            punchRepository.save(new AttendancePunch(UUID.randomUUID(), companyId, employee.getId(), today, now));
            if (record.getCheckIn() == null) {
                record.setCheckIn(now);
            }
            // Back in: the day's last check-out is now whichever comes next.
            record.setCheckOut(null);
            // Someone clocking in is present, unless an admin deliberately marked the day otherwise.
            if (record.getStatus() == AttendanceStatus.ABSENT) {
                record.setStatus(AttendanceStatus.PRESENT);
            }
        }
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return of(employee, user, today, record, false, null);
    }

    /** The signed-in employee checks out, closing the open session. The day's check-out is the last one. */
    @Transactional
    public AttendanceEntryResponse checkOut(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        LocalDate today = today(employee);
        AttendanceRecord record = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Check in first"));
        List<AttendancePunch> sessions = punchRepository.findByEmployeeIdAndDateOrderByCheckInAsc(employee.getId(), today);
        AttendancePunch last = sessions.isEmpty() ? null : sessions.get(sessions.size() - 1);
        LocalTime now = nowTime(employee);
        if (last == null && record.getCheckIn() != null) {
            // A day an admin opened with a check-in time but no session: that time starts the session.
            last = punchRepository.save(new AttendancePunch(UUID.randomUUID(), companyId, employee.getId(),
                    today, record.getCheckIn()));
        }
        if (last == null || last.getCheckOut() != null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "You're not checked in");
        }
        if (now.isBefore(last.getCheckIn())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Check-out can't be before check-in");
        }
        last.setCheckOut(now);
        record.setCheckOut(now);
        validateTimes(record);
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return of(employee, user, today, record, false, null);
    }

    /** Clear today's clock-in/out for the signed-in employee, so the day is open again. */
    @Transactional
    public AttendanceEntryResponse clearToday(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        LocalDate today = today(employee);
        attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today)
                .ifPresent(attendanceRepository::delete);
        punchRepository.deleteByEmployeeIdAndDate(employee.getId(), today);
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return resolve(employee, user, today, Optional.empty(),
                approvedLeaveFor(employee.getId(), today, today));
    }

    /** Today's row for the signed-in employee (null status when they haven't clocked in). */
    @Transactional(readOnly = true)
    public AttendanceEntryResponse today(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        LocalDate today = today(employee);
        return resolve(employee, user, today,
                attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today),
                approvedLeaveFor(employee.getId(), today, today));
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
     * The per-company facts a walk over many people needs, fetched once instead of per row.
     *
     * <p>Every performance defect found in this class has had the same shape: a fact that is the same
     * for the whole company, looked up again for every employee — holidays, and then department names.
     * Naming the pattern makes the next one harder to write, because a bulk caller now has an obvious
     * place to put what it has already fetched.
     *
     * <p>Null means "look it up", which is what every single-row caller does and should: one query for
     * one row is the right trade.
     */
    private record Prefetch(Map<LocalDate, Holiday> holidays, Map<UUID, String> departmentNames,
                            WorkFacts work) {}

    /**
     * Check-in sessions, rostered shifts and the company's attendance rules for a window, fetched
     * once (V70). Same reason as {@link Prefetch}: a day sheet must not ask the database for each
     * person's sessions in turn.
     *
     * @param punches  employee -> date -> that day's sessions, in check-in order
     * @param rostered employee -> date -> the shift they are rostered on
     * @param settings the company's settings: standard day, grace, and when the rules start
     */
    private record WorkFacts(Map<UUID, Map<LocalDate, List<AttendancePunch>>> punches,
                             Map<UUID, Map<LocalDate, ShiftFact>> rostered,
                             com.calyvora.company.CompanySettings settings) {}

    /** What a rostered shift says about one day. */
    private record ShiftFact(LocalTime start, int workMinutes) {}

    /** Everyone's sessions and rostered shifts over a window — four queries whatever the headcount. */
    private WorkFacts workFacts(UUID companyId, LocalDate from, LocalDate to) {
        return workFacts(companyId, punchRepository.findByCompanyIdAndDateBetween(companyId, from, to),
                assignmentRepository.slots(companyId, from, to));
    }

    /** One person's sessions and rostered shifts over a window. */
    private WorkFacts workFactsFor(Employee employee, LocalDate from, LocalDate to) {
        return workFacts(employee.getCompanyId(),
                punchRepository.findByEmployeeIdAndDateBetween(employee.getId(), from, to),
                assignmentRepository.slotsOf(employee.getId(), from, to));
    }

    private WorkFacts workFacts(UUID companyId, List<AttendancePunch> punchList,
                                List<com.calyvora.shift.ShiftSlot> assignments) {
        Map<UUID, Map<LocalDate, List<AttendancePunch>>> punches = new HashMap<>();
        for (AttendancePunch p : punchList) {
            punches.computeIfAbsent(p.getEmployeeId(), k -> new HashMap<>())
                    .computeIfAbsent(p.getDate(), k -> new ArrayList<>()).add(p);
        }
        for (Map<LocalDate, List<AttendancePunch>> days : punches.values()) {
            for (List<AttendancePunch> day : days.values()) {
                day.sort(java.util.Comparator.comparing(AttendancePunch::getCheckIn));
            }
        }
        Map<UUID, Map<LocalDate, ShiftFact>> rostered = new HashMap<>();
        if (!assignments.isEmpty()) {
            Map<UUID, ShiftFact> shifts = new HashMap<>();
            for (com.calyvora.shift.Shift s : shiftRepository.findByCompanyIdOrderByStartTimeAsc(companyId)) {
                shifts.put(s.getId(), new ShiftFact(s.getStartTime(), s.getWorkMinutes()));
            }
            for (com.calyvora.shift.ShiftSlot a : assignments) {
                ShiftFact fact = shifts.get(a.shiftId());
                if (fact != null) {
                    rostered.computeIfAbsent(a.employeeId(), k -> new HashMap<>()).put(a.onDate(), fact);
                }
            }
        }
        // A company that never opened its settings behaves exactly like one that saved the defaults.
        com.calyvora.company.CompanySettings settings = companySettingsRepository.findById(companyId)
                .orElseGet(() -> new com.calyvora.company.CompanySettings(companyId));
        return new WorkFacts(punches, rostered, settings);
    }

    /**
     * As above, with the company's holidays and departments for the period already in hand.
     */
    private AttendanceEntryResponse resolve(Employee employee, User user, LocalDate date,
                                            Optional<AttendanceRecord> marked, List<LeaveRequest> leave,
                                            Prefetch prefetch) {
        if (marked.isPresent()) {
            return of(employee, user, date, marked.get(), false, prefetch);
        }
        for (LeaveRequest lr : leave) {
            if (!date.isBefore(lr.getStartDate()) && !date.isAfter(lr.getEndDate())) {
                return entry(employee, user, date, AttendanceStatus.ON_LEAVE.name(), null, null,
                        lr.getType().name().toLowerCase() + (lr.getReason() == null ? "" : " Â· " + lr.getReason()),
                        true, prefetch);
            }
        }
        // A company holiday closes the day for everyone (unless someone marked otherwise above).
        //
        // Read from a prefetched map when the caller has one. This lookup used to be a query PER DAY
        // PER EMPLOYEE — a month for one person was thirty queries, and a payroll run over a thousand
        // people was thirty thousand. It is the single most expensive thing in this class and it was
        // invisible, because at seven employees thirty queries a head is unnoticeable.
        Optional<Holiday> holiday = (prefetch != null && prefetch.holidays() != null
                ? Optional.ofNullable(prefetch.holidays().get(date))
                : holidayRepository
                        .findByCompanyIdAndDateBetweenOrderByDateAsc(employee.getCompanyId(), date, date).stream()
                        .findFirst())
                .filter(h -> !h.isOptional());
        if (holiday.isPresent()) {
            return entry(employee, user, date, AttendanceStatus.HOLIDAY.name(), null, null,
                    holiday.get().getName(), true, prefetch);
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return entry(employee, user, date, AttendanceStatus.WEEK_OFF.name(), null, null, null, true, prefetch);
        }
        // A working day with nothing recorded: absent once shift start plus the company's grace has
        // passed (V70). Before that — or before the rules were switched on — it is still unmarked.
        WorkFacts work = workFor(employee, date, prefetch);
        // Nobody is absent from a job they had not started yet, or had already left.
        boolean employed = (employee.getStartDate() == null || !date.isBefore(employee.getStartDate()))
                && (employee.getEndDate() == null || !date.isAfter(employee.getEndDate()));
        if (employed && rulesApply(work, date)) {
            java.time.ZoneId zone = Timezones.resolve(employee, work.settings());
            LocalDate today = LocalDate.now(zone);
            LocalTime cutoff = shiftStart(employee, date, work).plusMinutes(work.settings().getAbsentGraceMinutes());
            boolean pastCutoff = date.isBefore(today)
                    || (date.equals(today) && !LocalTime.now(zone).isBefore(cutoff));
            if (pastCutoff) {
                return entry(employee, user, date, AttendanceStatus.ABSENT.name(), null, null,
                        "No check-in by " + cutoff, true, prefetch);
            }
        }
        return entry(employee, user, date, null, null, null, null, true, prefetch);
    }

    /** Whether absent-after-grace and short-day-is-half-day reach this date. */
    private static boolean rulesApply(WorkFacts work, LocalDate date) {
        LocalDate from = work.settings().getAttendanceRulesFrom();
        return from != null && !date.isBefore(from);
    }

    /** When this person's day starts: their rostered shift, else the company's standard start. */
    private static LocalTime shiftStart(Employee employee, LocalDate date, WorkFacts work) {
        ShiftFact shift = work.rostered().getOrDefault(employee.getId(), Map.of()).get(date);
        return shift != null ? shift.start() : work.settings().getWorkDayStart();
    }

    private AttendanceEntryResponse of(Employee employee, User user, LocalDate date,
                                       AttendanceRecord r, boolean derived, Prefetch prefetch) {
        WorkFacts work = workFor(employee, date, prefetch);
        List<AttendancePunch> punches = work.punches().getOrDefault(employee.getId(), Map.of())
                .getOrDefault(date, List.of());

        // Effective time is what was spent checked in: the closed sessions added up. A day an admin
        // entered by hand has no sessions, only a first in and a last out, and that span is the day.
        List<AttendanceEntryResponse.Session> sessions = new ArrayList<>();
        int effective = 0;
        String openSince = null;
        for (AttendancePunch p : punches) {
            sessions.add(new AttendanceEntryResponse.Session(p.getCheckIn().toString(),
                    p.getCheckOut() == null ? null : p.getCheckOut().toString()));
            if (p.getCheckOut() == null) {
                openSince = p.getCheckIn().toString();
            } else {
                effective += minutesBetween(p.getCheckIn(), p.getCheckOut());
            }
        }
        Integer gross = r.getCheckIn() != null && r.getCheckOut() != null
                ? minutesBetween(r.getCheckIn(), r.getCheckOut()) : null;
        if (punches.isEmpty() && gross != null) {
            effective = gross;
        }
        boolean anyTime = !punches.isEmpty() || r.getCheckIn() != null;
        int required = requiredMinutes(employee, date, work);

        // A finished day the employee clocked themselves, short of the required hours, is a half day
        // — and a half day costs half a day's pay. A day a manager or HR set by hand (markedBy) is
        // their decision and stands, which is how a short day is regularized.
        AttendanceStatus status = r.getStatus();
        String note = r.getNote();
        boolean dayOver = date.isBefore(LocalDate.now(Timezones.resolve(employee, work.settings())));
        if (status == AttendanceStatus.PRESENT && r.getMarkedBy() == null && anyTime && dayOver
                && rulesApply(work, date) && effective < required) {
            status = AttendanceStatus.HALF_DAY;
            derived = true;
            note = "Short by " + hoursAndMinutes(required - effective);
        }

        return new AttendanceEntryResponse(employee.getId().toString(), displayName(user), employee.getJobTitle(),
                departmentName(employee, prefetch == null ? null : prefetch.departmentNames()),
                date.toString(), status.name(),
                r.getCheckIn() == null ? null : r.getCheckIn().toString(),
                r.getCheckOut() == null ? null : r.getCheckOut().toString(),
                note, derived, sessions, gross, anyTime ? effective : null, openSince,
                status.isWorking() ? required : null);
    }

    private static String hoursAndMinutes(int minutes) {
        return minutes / 60 + "h " + String.format("%02d", minutes % 60) + "m";
    }

    private AttendanceEntryResponse entry(Employee employee, User user, LocalDate date, String status,
                                          String checkIn, String checkOut, String note, boolean derived,
                                          Prefetch prefetch) {
        // Only a working day still has hours owed; leave, holidays and weekends do not.
        Integer required = status == null || AttendanceStatus.ABSENT.name().equals(status)
                ? requiredMinutes(employee, date, workFor(employee, date, prefetch)) : null;
        return new AttendanceEntryResponse(employee.getId().toString(), displayName(user), employee.getJobTitle(),
                departmentName(employee, prefetch == null ? null : prefetch.departmentNames()),
                date.toString(), status, checkIn, checkOut, note, derived, List.of(), null, null, null, required);
    }

    private static String displayName(User user) {
        return user == null ? "Employee" : (user.getFirstName() + " " + user.getLastName()).trim();
    }

    /** The bulk facts when the caller fetched them, else this one person's for this one day. */
    private WorkFacts workFor(Employee employee, LocalDate date, Prefetch prefetch) {
        return prefetch != null && prefetch.work() != null ? prefetch.work() : workFactsFor(employee, date, date);
    }

    /** The rostered shift's hours that day, else the company's standard day. */
    private static int requiredMinutes(Employee employee, LocalDate date, WorkFacts work) {
        ShiftFact shift = work.rostered().getOrDefault(employee.getId(), Map.of()).get(date);
        return shift != null ? shift.workMinutes() : work.settings().getWorkDayMinutes();
    }

    private static int minutesBetween(LocalTime from, LocalTime to) {
        return to.isBefore(from) ? 0 : (int) java.time.Duration.between(from, to).toMinutes();
    }

    /**
     * Today on the company's clock. The day sheet and dashboard used the server's date, and the
     * server runs on UTC: from midnight to 05:30 in India every admin was looking at yesterday.
     */
    @Transactional(readOnly = true)
    public LocalDate companyToday() {
        return LocalDate.now(Timezones.forCompany(
                companySettingsRepository.findById(TenantContext.getCompanyId()).orElse(null)));
    }

    /**
     * Department name, for the "who's out in this team" breakdown.
     *
     * <p>The old comment here said this stayed cheap because a company has a handful of departments
     * and the lookup is by primary key. Both halves were true and the conclusion was still wrong: it
     * ran <em>once per row</em>. A day sheet for a thousand people asked the database a thousand times
     * which of six departments somebody was in. Cheap per call is not the same as cheap.
     *
     * <p>Bulk callers pass the names in. Single-row callers pass nothing and still pay one query,
     * which for one row is the right trade.
     */
    private String departmentName(Employee employee, Map<UUID, String> prefetched) {
        if (employee.getDepartmentId() == null) {
            return null;
        }
        if (prefetched != null) {
            return prefetched.get(employee.getDepartmentId());
        }
        return departmentRepository.findByIdAndCompanyId(employee.getDepartmentId(), employee.getCompanyId())
                .map(Department::getName).orElse(null);
    }

    /** Every department name in the company, by id — one query, for a walk over many people. */
    private Map<UUID, String> departmentNames(UUID companyId) {
        Map<UUID, String> names = new HashMap<>();
        for (Department d : departmentRepository.findByCompanyIdOrderByName(companyId)) {
            names.put(d.getId(), d.getName());
        }
        return names;
    }

    // ---- helpers ----

    /**
     * Approved leave for the whole company, bounded to the dates being asked about.
     *
     * <p>This used to read every leave request the company had ever filed — for a day sheet, for a
     * month, and even to decide whether one person is on leave today. The filtering then happened in
     * Java. A young company has a few hundred rows and nobody notices; two years in it is tens of
     * thousands, fetched on a screen that people open every morning.
     *
     * <p>The window is the question. Anything that neither starts before the window ends nor ends
     * after it begins cannot affect a single day in it, so the database should never have sent it.
     */
    private Map<UUID, List<LeaveRequest>> approvedLeaveByEmployee(UUID companyId, LocalDate from, LocalDate to) {
        Map<UUID, List<LeaveRequest>> byEmployee = new HashMap<>();
        for (LeaveRequest lr : leaveRepository
                .findByCompanyIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        companyId, LeaveStatus.APPROVED, to, from)) {
            byEmployee.computeIfAbsent(lr.getEmployeeId(), k -> new ArrayList<>()).add(lr);
        }
        return byEmployee;
    }

    /**
     * Approved leave for one person over a window. Goes by employee id rather than filtering a
     * company-wide fetch — asking "am I on leave today" should not read anybody else's leave.
     */
    private List<LeaveRequest> approvedLeaveFor(UUID employeeId, LocalDate from, LocalDate to) {
        List<LeaveRequest> out = new ArrayList<>();
        for (LeaveRequest lr : leaveRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)) {
            if (lr.getStatus() == LeaveStatus.APPROVED
                    && !lr.getStartDate().isAfter(to) && !lr.getEndDate().isBefore(from)) {
                out.add(lr);
            }
        }
        return out;
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
