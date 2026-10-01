package com.calyvora.shift;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.people.EmployeeService;
import com.calyvora.people.dto.EmployeeResponse;
import com.calyvora.shift.dto.RosterResponse;
import com.calyvora.shift.dto.ShiftPayload;
import com.calyvora.shift.dto.ShiftResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Shift scheduling / rostering (Owner/Admin). Shifts are reusable templates; the roster assigns one
 * employee to one shift on one day (at most one shift per employee per day). Tenant-scoped throughout.
 */
@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final EmployeeService employeeService;

    public ShiftService(ShiftRepository shiftRepository, ShiftAssignmentRepository assignmentRepository,
                        EmployeeService employeeService) {
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeService = employeeService;
    }

    // ---- shift templates ----

    @Transactional(readOnly = true)
    public List<ShiftResponse> shifts() {
        UUID companyId = TenantContext.getCompanyId();
        return shiftRepository.findByCompanyIdOrderByStartTimeAsc(companyId).stream()
                .map(ShiftResponse::of).toList();
    }

    @Transactional
    public ShiftResponse createShift(ShiftPayload req) {
        UUID companyId = TenantContext.getCompanyId();
        Shift shift = new Shift(UUID.randomUUID(), companyId, req.name().trim(),
                parseTime(req.startTime()), parseTime(req.endTime()), blankToNull(req.color()));
        shiftRepository.save(shift);
        return ShiftResponse.of(shift);
    }

    @Transactional
    public ShiftResponse updateShift(UUID id, ShiftPayload req) {
        UUID companyId = TenantContext.getCompanyId();
        Shift shift = requireShift(id, companyId);
        if (req.name() != null && !req.name().isBlank()) shift.setName(req.name().trim());
        if (req.startTime() != null && !req.startTime().isBlank()) shift.setStartTime(parseTime(req.startTime()));
        if (req.endTime() != null && !req.endTime().isBlank()) shift.setEndTime(parseTime(req.endTime()));
        if (req.color() != null) shift.setColor(blankToNull(req.color()));
        return ShiftResponse.of(shift);
    }

    @Transactional
    public void deleteShift(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        shiftRepository.delete(requireShift(id, companyId));
    }

    // ---- roster ----

    @Transactional
    public RosterResponse roster(LocalDate weekStart) {
        UUID companyId = TenantContext.getCompanyId();
        LocalDate start = weekStart == null ? mondayOf(LocalDate.now()) : mondayOf(weekStart);
        LocalDate end = start.plusDays(6);

        List<String> days = start.datesUntil(end.plusDays(1)).map(LocalDate::toString).toList();
        List<ShiftResponse> shifts = shiftRepository.findByCompanyIdOrderByStartTimeAsc(companyId).stream()
                .map(ShiftResponse::of).toList();

        List<RosterResponse.RosterEmployee> employees = employeeService.directory().stream()
                .map(e -> new RosterResponse.RosterEmployee(e.id(), fullName(e), e.jobTitle()))
                .toList();

        List<RosterResponse.RosterEntry> assignments =
                assignmentRepository.findByCompanyIdAndOnDateBetween(companyId, start, end).stream()
                        .map(a -> new RosterResponse.RosterEntry(a.getId().toString(),
                                a.getEmployeeId().toString(), a.getShiftId().toString(), a.getOnDate().toString()))
                        .toList();

        return new RosterResponse(start.toString(), days, shifts, employees, assignments);
    }

    /**
     * Roster an employee onto a shift on a day. Upsert: at most one shift per employee per day, so a
     * second assign for the same day moves them to the new shift rather than creating a duplicate.
     */
    @Transactional
    public RosterResponse.RosterEntry assign(UUID employeeId, LocalDate onDate, UUID shiftId) {
        UUID companyId = TenantContext.getCompanyId();
        requireShift(shiftId, companyId);
        ShiftAssignment existing = assignmentRepository
                .findByCompanyIdAndEmployeeIdAndOnDate(companyId, employeeId, onDate).orElse(null);
        if (existing != null) {
            existing.setShiftId(shiftId);
            return toEntry(existing);
        }
        ShiftAssignment a = new ShiftAssignment(UUID.randomUUID(), companyId, employeeId, shiftId, onDate);
        assignmentRepository.save(a);
        return toEntry(a);
    }

    @Transactional
    public void unassign(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        ShiftAssignment a = assignmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Assignment not found"));
        assignmentRepository.delete(a);
    }

    // ---- helpers ----

    private static RosterResponse.RosterEntry toEntry(ShiftAssignment a) {
        return new RosterResponse.RosterEntry(a.getId().toString(), a.getEmployeeId().toString(),
                a.getShiftId().toString(), a.getOnDate().toString());
    }

    private static String fullName(EmployeeResponse e) {
        String first = e.firstName() == null ? "" : e.firstName();
        String last = e.lastName() == null ? "" : e.lastName();
        return (first + " " + last).trim();
    }

    private static LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - 1L);
    }

    private Shift requireShift(UUID id, UUID companyId) {
        return shiftRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Shift not found"));
    }

    private static LocalTime parseTime(String s) {
        try {
            return LocalTime.parse(s.trim());
        } catch (DateTimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid time (expected HH:mm)");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
