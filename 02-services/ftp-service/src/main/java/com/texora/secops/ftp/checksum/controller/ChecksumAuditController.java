package com.texora.secops.ftp.checksum.controller;

import com.texora.secops.ftp.checksum.service.ChecksumVerificationService;
import com.texora.secops.ftp.dto.ChecksumRecordResponse;
import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.ftp.security.RequiresRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checksum")
public class ChecksumAuditController {

    private final ChecksumVerificationService service;

    public ChecksumAuditController(ChecksumVerificationService service) {
        this.service = service;
    }

    @GetMapping("/audit-tracking")
    @RequiresRole("ftp.checksum.read")
    public PageResponse<ChecksumRecordResponse> auditTracking(@RequestParam(required = false) UUID transferJobId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "50") int size) {
        return service.list(CallerAuthentication.require().getTenantId(), transferJobId, page, size);
    }
}
