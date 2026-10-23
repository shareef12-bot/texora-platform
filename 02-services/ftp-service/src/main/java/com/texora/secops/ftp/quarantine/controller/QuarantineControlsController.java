package com.texora.secops.ftp.quarantine.controller;

import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.dto.PlaceQuarantineRequest;
import com.texora.secops.ftp.dto.QuarantineRecordResponse;
import com.texora.secops.ftp.dto.ReleaseQuarantineRequest;
import com.texora.secops.ftp.quarantine.service.QuarantineService;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.ftp.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quarantine-controls")
public class QuarantineControlsController {

    private final QuarantineService service;

    public QuarantineControlsController(QuarantineService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("ftp.quarantine.read")
    public PageResponse<QuarantineRecordResponse> list(@RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "50") int size) {
        return service.list(CallerAuthentication.require().getTenantId(), status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole("ftp.quarantine.write")
    public QuarantineRecordResponse place(@Valid @RequestBody PlaceQuarantineRequest request) {
        return service.placeManually(CallerAuthentication.require(), request);
    }

    @PostMapping("/{id}/release")
    @RequiresRole("ftp.quarantine.release")
    public QuarantineRecordResponse release(@PathVariable UUID id, @Valid @RequestBody ReleaseQuarantineRequest request) {
        return service.release(CallerAuthentication.require(), id, request);
    }
}
