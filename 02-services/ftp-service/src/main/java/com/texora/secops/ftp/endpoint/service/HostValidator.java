package com.texora.secops.ftp.endpoint.service;

import com.texora.secops.ftp.exception.UnprocessableException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Registration-time host validation. Rejects obviously internal targets (SSRF). The adapter repeats the check on
 * the RESOLVED address at connect time, which is what actually defeats DNS tricks.
 */
final class HostValidator {

    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?=.{1,253}$)[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private HostValidator() {
    }

    static String validate(String host) {
        String h = host == null ? "" : host.trim();
        if (h.indexOf(':') >= 0) {
            throw new UnprocessableException("ENDPOINT_HOST_INVALID", "IPv6 literals are not supported; use a hostname");
        }
        if (!HOSTNAME.matcher(h).matches()) {
            throw new UnprocessableException("ENDPOINT_HOST_INVALID", "Host is not a valid hostname or IPv4 address");
        }
        String lower = h.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".localhost")) {
            throw new UnprocessableException("ENDPOINT_HOST_FORBIDDEN", "Host is in a forbidden address range");
        }
        if (IPV4.matcher(h).matches()) {
            try {
                InetAddress a = InetAddress.getByName(h);   // literal: no DNS lookup
                if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress() || a.isMulticastAddress()) {
                    throw new UnprocessableException("ENDPOINT_HOST_FORBIDDEN", "Host is in a forbidden address range");
                }
            } catch (UnknownHostException e) {
                throw new UnprocessableException("ENDPOINT_HOST_INVALID", "Host is not a valid IPv4 address");
            }
        }
        return lower;
    }
}
