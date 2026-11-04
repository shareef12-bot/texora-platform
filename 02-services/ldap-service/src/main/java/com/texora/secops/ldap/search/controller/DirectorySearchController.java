package com.texora.secops.ldap.search.controller;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.ldap.search.dto.DirectorySearchRequest;
import com.texora.secops.ldap.search.dto.DirectorySearchResponse;
import com.texora.secops.ldap.search.service.DirectorySearchService;
import com.texora.secops.ldap.security.RbacInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/directory")
public class DirectorySearchController {

    private final DirectorySearchService directorySearchService;

    public DirectorySearchController(DirectorySearchService directorySearchService) {
        this.directorySearchService = directorySearchService;
    }

    @PostMapping("/search")
    public ResponseEntity<DirectorySearchResponse> search(@RequestBody DirectorySearchRequest body,
                                                           HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        return ResponseEntity.ok(directorySearchService.search(body, claims.getSubject(), claims.getTenantId()));
    }
}
