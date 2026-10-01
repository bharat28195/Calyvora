package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.HolidayPayload;
import com.calyvora.people.dto.HolidayResponse;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

/** The company holiday calendar. Readable by everyone; only Owner/Admin can change it. */
@RestController
@RequestMapping("/api/v1/people/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    public List<HolidayResponse> list(@RequestParam(required = false) Integer year) {
        return holidayService.list(year);
    }

    @GetMapping("/upcoming")
    public List<HolidayResponse> upcoming(@RequestParam(defaultValue = "5") int limit) {
        return holidayService.upcoming(limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public HolidayResponse create(@Valid @RequestBody HolidayPayload payload,
                                  @CurrentUser AuthPrincipal principal) {
        return holidayService.create(payload, principal);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public HolidayResponse update(@PathVariable UUID id, @Valid @RequestBody HolidayPayload payload) {
        return holidayService.update(id, payload);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public void delete(@PathVariable UUID id) {
        holidayService.delete(id);
    }

    /** One-click starter calendar for a company that has none yet. */
    @PostMapping("/defaults")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public List<HolidayResponse> seedDefaults(@CurrentUser AuthPrincipal principal) {
        return holidayService.seedDefaults(principal);
    }
}
