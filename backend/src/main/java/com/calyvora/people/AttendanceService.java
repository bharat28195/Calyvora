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
 * Daily attendance (feedback C.4 — phase 2 of the "attendance option"). Phase 1 derived present vs
 * on-leave from approved leave; this stores a real row per employee per day.
 *
 * <p>Two rules keep it from becoming double data entry:
 * <ul>
 *   <li><b>Approved leave fills the day automatically.</b> Nobody marks time off twice — an
 *       unmarked day covered by approved leave resolves to {@code ON_LEAVE} (flagged {@code derived}).</li>
 *   <li><b>A marked row always wins</b> over anything derived, so a correction sticks.</li>
 * </ul>
 * Company holidays fill the day the same way; weekends resolve to {@code WEEK_OFF}. The work-week
 * itself is still hardcoded Mon–Fri — configurable work-week policy is the remaining debt here.
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

    public AttendanceService(AttendanceRepository attendanceRepository, EmployeeRepository employeeRepository,
                             EmployeeService employeeService, DepartmentRepository departmentRepository,
                             HolidayRepository holidayRepository,
                             UserRepository userRepository, LeaveRequestRepository leaveRepository) {
        this.holidayRepository = holidayRepository;
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.leaveRepository = leaveRepository;
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

        List<AttendanceEntryResponse> days = new ArrayList<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AttendanceStatus s : AttendanceStatus.values()) {
            counts.put(s.name(), 0L);
        }
        double worked = 0;
        long expected = 0;
        LocalDate today = LocalDate.now();

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceEntryResponse entry = resolve(employee, user, d,
                    Optional.ofNullable(marked.get(d)), leave);
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
        return new AttendanceMonthResponse(employeeId.toString(), name, month.toString(), days, counts,
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
        LocalDate date = req.date() == null || req.date().isBlank() ? LocalDate.now() : LocalDate.parse(req.date());
        if (date.isAfter(LocalDate.now())) {
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

    /** The signed-in employee clocks in for today. Idempotent — a second call won't move the time. */
    @Transactional
    public AttendanceEntryResponse checkIn(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        LocalDate today = LocalDate.now();
        AttendanceRecord record = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today)
                .orElseGet(() -> attendanceRepository.save(new AttendanceRecord(UUID.randomUUID(), companyId,
                        employee.getId(), today, AttendanceStatus.PRESENT, null)));
        if (record.getCheckIn() == null) {
            record.setCheckIn(LocalTime.now().withSecond(0).withNano(0));
            // Someone clocking in is present, unless an admin deliberately marked the day otherwise.
            if (record.getStatus() == AttendanceStatus.ABSENT) {
                record.setStatus(AttendanceStatus.PRESENT);
            }
        }
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return of(employee, user, today, record, false);
    }

    /** The signed-in employee clocks out. Later calls overwrite — leaving twice means the later one. */
    @Transactional
    public AttendanceEntryResponse checkOut(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        LocalDate today = LocalDate.now();
        AttendanceRecord record = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Check in first"));
        record.setCheckOut(LocalTime.now().withSecond(0).withNano(0));
        validateTimes(record);
        User user = userRepository.findByIdAndCompanyId(employee.getUserId(), companyId).orElse(null);
        return of(employee, user, today, record, false);
    }

    /** Clear today's clock-in/out for the signed-in employee, so the day is open again. */
    @Transactional
    public AttendanceEntryResponse clearToday(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Employee employee = requireSelf(companyId, principal);
        LocalDate today = LocalDate.now();
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
        LocalDate today = LocalDate.now();
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
     * unmarked (null status) — which the UI shows as "not marked yet" rather than inventing a value.
     */
    private AttendanceEntryResponse resolve(Employee employee, User user, LocalDate date,
                                            Optional<AttendanceRecord> marked, List<LeaveRequest> leave) {
        if (marked.isPresent()) {
            return of(employee, user, date, marked.get(), false);
        }
        for (LeaveRequest lr : leave) {
            if (!date.isBefore(lr.getStartDate()) && !date.isAfter(lr.getEndDate())) {
                return entry(employee, user, date, AttendanceStatus.ON_LEAVE.name(), null, null,
                        lr.getType().name().toLowerCase() + (lr.getReason() == null ? "" : " · " + lr.getReason()),
                        true);
            }
        }
        // A company holiday closes the day for everyone (unless someone marked otherwise above).
        Optional<Holiday> holiday = holidayRepository
                .findByCompanyIdAndDateBetweenOrderByDateAsc(employee.getCompanyId(), date, date).stream()
                .filter(h -> !h.isOptional())
                .findFirst();
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
