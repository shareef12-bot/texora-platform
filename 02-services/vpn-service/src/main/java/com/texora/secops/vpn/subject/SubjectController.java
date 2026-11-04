package com.texora.secops.vpn.subject;

import com.texora.secops.vpn.dto.DeviceEnrollmentRequest;
import com.texora.secops.vpn.dto.DeviceEnrollmentResponse;
import com.texora.secops.vpn.dto.PageResponse;
import com.texora.secops.vpn.dto.SubjectRequest;
import com.texora.secops.vpn.dto.SubjectResponse;
import com.texora.secops.vpn.security.CallerResolver;
import com.texora.secops.vpn.security.RequiresRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Subject and device-enrollment administration. Not in LLD s6.1's endpoint table but required by it: LLD s9.4 says
 * device-enrollment endpoints must be scoped as tightly as policy approval, and sessions cannot exist without subjects.
 */
@RestController
@RequestMapping(value = "/api/v1/subjects", produces = MediaType.APPLICATION_JSON_VALUE)
public class SubjectController {

    private final VpnSubjectService subjectService;
    private final DeviceEnrollmentService deviceService;
    private final CallerResolver callers;

    public SubjectController(VpnSubjectService subjectService, DeviceEnrollmentService deviceService, CallerResolver callers) {
        this.subjectService = subjectService;
        this.deviceService = deviceService;
        this.callers = callers;
    }

    @GetMapping
    @RequiresRole("vpn.subject.read")
    public PageResponse<SubjectResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return subjectService.list(callers.current().getTenantId(), page, size);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole("vpn.subject.write")
    public SubjectResponse create(@Valid @RequestBody SubjectRequest request) {
        return subjectService.create(callers.current(), request.getDcUserId());
    }

    @GetMapping("/{subjectId}/devices")
    @RequiresRole("vpn.device.read")
    public List<DeviceEnrollmentResponse> devices(@PathVariable UUID subjectId) {
        return deviceService.list(callers.current().getTenantId(), subjectId);
    }

    @PostMapping(value = "/{subjectId}/devices", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole("vpn.device.write")
    public DeviceEnrollmentResponse enroll(@PathVariable UUID subjectId, @Valid @RequestBody DeviceEnrollmentRequest request) {
        return deviceService.enroll(callers.current(), subjectId, request);
    }
}
