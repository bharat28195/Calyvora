package com.calyvora.payroll;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Statutory return files, from finalised months — payroll managers only.
 *
 * <p>Each file has two forms: {@code ?format=json} returns the content with the list of people left
 * out and why, for the screen to show before anyone downloads; the default is the file itself.
 */
@RestController
@RequestMapping("/api/v1/payroll/filings")
@PreAuthorize("@perm.has('PAYROLL_MANAGE')")
public class StatutoryFilingController {

    private final StatutoryFilingService service;

    public StatutoryFilingController(StatutoryFilingService service) {
        this.service = service;
    }

    @GetMapping("/readiness")
    public List<StatutoryFilingService.Issue> readiness() {
        return service.readiness();
    }

    @GetMapping("/{month}/ecr")
    public ResponseEntity<?> ecr(@PathVariable String month, @RequestParam(required = false) String format) {
        return respond(service.ecr(month), format);
    }

    @GetMapping("/{month}/esi")
    public ResponseEntity<?> esi(@PathVariable String month, @RequestParam(required = false) String format) {
        return respond(service.esi(month), format);
    }

    @GetMapping("/{month}/pt")
    public ResponseEntity<?> pt(@PathVariable String month, @RequestParam(required = false) String format) {
        return respond(service.professionalTax(month), format);
    }

    @GetMapping("/{month}/lwf")
    public ResponseEntity<?> lwf(@PathVariable String month, @RequestParam(required = false) String format) {
        return respond(service.labourWelfareFund(month), format);
    }

    @GetMapping("/24q/{quarter}")
    public ResponseEntity<?> form24q(@PathVariable String quarter, @RequestParam(required = false) String format) {
        return respond(service.form24q(quarter), format);
    }

    private static ResponseEntity<?> respond(StatutoryFilingService.FilingFile file, String format) {
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(file);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.contentType() + "; charset=utf-8"))
                .body(file.content().getBytes(StandardCharsets.UTF_8));
    }
}
