package com.calyvora.shift;

import com.calyvora.shift.dto.RosterResponse;
import com.calyvora.shift.dto.ShiftPayload;
import com.calyvora.shift.dto.ShiftResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shift scheduling / rostering (Owner/Admin). Base {@code /api/v1/shifts}. */
@RestController
@RequestMapping("/api/v1/shifts")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
public class ShiftController {

    private final ShiftService service;

    public ShiftController(ShiftService service) {
        this.service = service;
    }

    // ---- shift templates ----

    @GetMapping
    public List<ShiftResponse> shifts() {
        return service.shifts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftResponse createShift(@Valid @RequestBody ShiftPayload req) {
        return service.createShift(req);
    }

    @PatchMapping("/{id}")
    public ShiftResponse updateShift(@PathVariable UUID id, @RequestBody ShiftPayload req) {
        return service.updateShift(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShift(@PathVariable UUID id) {
        service.deleteShift(id);
    }

    // ---- roster ----

    @GetMapping("/roster")
    public RosterResponse roster(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return service.roster(weekStart);
    }

    @PostMapping("/roster/assign")
    public RosterResponse.RosterEntry assign(@RequestBody Map<String, String> body) {
        return service.assign(UUID.fromString(body.get("employeeId")),
                LocalDate.parse(body.get("onDate")), UUID.fromString(body.get("shiftId")));
    }

    @DeleteMapping("/roster/assign/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable UUID id) {
        service.unassign(id);
    }
}
