package com.texora.secops.ftp.job.controller;

import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.dto.SubmitTransferJobRequest;
import com.texora.secops.ftp.dto.SubmitTransferJobResponse;
import com.texora.secops.ftp.dto.TransferJobResponse;
import com.texora.secops.ftp.job.service.TransferJobService;
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
@RequestMapping("/api/v1/transfer-jobs")
public class TransferJobController {

    private final TransferJobService service;

    public TransferJobController(TransferJobService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresRole("ftp.job.read")
    public PageResponse<TransferJobResponse> list(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return service.list(CallerAuthentication.require().getTenantId(), status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole("ftp.job.write")
    public SubmitTransferJobResponse submit(@Valid @RequestBody SubmitTransferJobRequest request) {
        return service.submit(CallerAuthentication.require(), request);
    }

    @GetMapping("/{id}")
    @RequiresRole("ftp.job.read")
    public TransferJobResponse get(@PathVariable UUID id) {
        return service.get(CallerAuthentication.require().getTenantId(), id);
    }

    @PostMapping("/{id}/cancel")
    @RequiresRole("ftp.job.write")
    public TransferJobResponse cancel(@PathVariable UUID id) {
        return service.cancel(CallerAuthentication.require(), id);
    }
}
