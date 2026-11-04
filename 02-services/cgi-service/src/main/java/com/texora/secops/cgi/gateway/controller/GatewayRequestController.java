package com.texora.secops.cgi.gateway.controller;

import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.execution.service.ExecutionOrchestrationService;
import com.texora.secops.cgi.gateway.dto.GatewayCall;
import com.texora.secops.cgi.gateway.dto.GatewayResponse;
import com.texora.secops.cgi.security.AuthenticatedOnly;
import com.texora.secops.cgi.security.CallerContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * ANY /gateway/{route} - the controlled front door. Mapping only: all decisions are in the services. The
 * Authorization header is never forwarded; the handler id comes from the approved route, never from the request.
 */
@RestController
public class GatewayRequestController {

    private static final String PREFIX = "/gateway";

    private final ExecutionOrchestrationService orchestration;
    private final ExecutionProperties properties;

    public GatewayRequestController(ExecutionOrchestrationService orchestration, ExecutionProperties properties) {
        this.orchestration = orchestration;
        this.properties = properties;
    }

    @RequestMapping(path = PREFIX + "/**")
    @AuthenticatedOnly
    public ResponseEntity<byte[]> handle(HttpServletRequest request,
                                         @RequestAttribute(CallerContext.REQUEST_ATTRIBUTE) CallerContext caller) {
        // Raw (still-encoded) URI on purpose: an encoded or odd path simply fails RoutePatterns and is a 404.
        String uri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String path = uri.startsWith(context) ? uri.substring(context.length()) : uri;
        String routePath = path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : path;

        Map<String, String[]> parameters = request.getParameterMap();   // form posts are consumed here
        byte[] body;
        try {
            int cap = (int) Math.min(properties.getMaxRequestBodyBytes(), Integer.MAX_VALUE - 1L);
            body = request.getInputStream().readNBytes(cap + 1);
            if (body.length > cap) {
                throw CgiApiException.unprocessable("BODY_TOO_LARGE", "Request body exceeds the allowed size");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        GatewayResponse response = orchestration.handle(
                new GatewayCall(caller, request.getMethod(), routePath, parameters, request.getContentType(), body));

        return ResponseEntity.status(response.getStatus())
                .header(HttpHeaders.CONTENT_TYPE, response.getContentType())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(response.getBody());
    }
}
