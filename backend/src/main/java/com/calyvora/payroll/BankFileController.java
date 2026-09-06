package com.calyvora.payroll;

import com.calyvora.payroll.dto.BankFilePreviewResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * The bulk salary transfer file.
 *
 * <p>HR and admins only, and deliberately narrower than it looks: this is the one endpoint in the
 * product that returns unmasked bank account numbers, because a bank file without them is not a bank
 * file. Everything else masks them.
 */
@RestController
@RequestMapping("/api/v1/payroll/bank-file")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
public class BankFileController {

    private final BankFileService service;

    public BankFileController(BankFileService service) {
        this.service = service;
    }

    /** What the file would contain, and who cannot be paid — without downloading anything. */
    @GetMapping("/preview")
    public BankFilePreviewResponse preview(@RequestParam(required = false) String month,
                                           @RequestParam(required = false) String format) {
        BankFileFormat f = BankFileFormat.parse(format);
        return BankFilePreviewResponse.of(service.preview(month, f), f);
    }

    /**
     * The file itself.
     *
     * <p>Returned as a download rather than as JSON the browser assembles: the content is a bank
     * instruction, and it should arrive as one file that can be handed to net banking unchanged.
     */
    @GetMapping
    public ResponseEntity<byte[]> download(@RequestParam(required = false) String month,
                                           @RequestParam(required = false) String format) {
        BankFileFormat f = BankFileFormat.parse(format);
        BankFileBuilder.Result result = service.preview(month, f);
        if (result.included().isEmpty()) {
            // A header-only file looks downloadable and pays nobody, and it can still be uploaded.
            throw new com.calyvora.common.error.ApiException(
                    com.calyvora.common.error.ErrorCode.VALIDATION_ERROR,
                    "Nobody can be paid this month — check the bank details on the payroll screen first");
        }
        byte[] body = result.csv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + service.fileNameFor(month, f) + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
