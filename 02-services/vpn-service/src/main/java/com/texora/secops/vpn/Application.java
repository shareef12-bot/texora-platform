package com.texora.secops.vpn;

import com.texora.secops.vpn.config.VpnProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * VPN Access Control Plane. CONTROL PLANE ONLY: this service never implements tunneling,
 * key exchange or packet-level cryptography. All dataplane access goes through
 * {@link com.texora.secops.vpn.gateway.GatewayIntegrationAdapter}.
 */
@SpringBootApplication(scanBasePackages = "com.texora.secops.vpn")
@EnableConfigurationProperties(VpnProperties.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
