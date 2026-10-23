package com.texora.secops.vpn.gateway;

import com.texora.secops.vpn.exception.VpnException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayEndpointServiceTest {

    @Test
    void acceptsPlainHttps() {
        assertThatCode(() -> GatewayEndpointService.validateUrl("https://gw.corp.example:8443/base")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnythingThatCouldCarryASecretOrDowngradeTransport() {
        for (String bad : new String[]{"http://gw.example", "https://user:pw@gw.example", "https://gw.example/?key=abc",
                "https://gw.example/#frag", "gw.example", "ftp://gw.example", "https://", "not a url"}) {
            assertThatThrownBy(() -> GatewayEndpointService.validateUrl(bad)).as(bad).isInstanceOf(VpnException.class);
        }
    }
}
