package com.infrawatch.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infrawatch.monitoring.MonitoringProperties.TargetPolicy;
import com.infrawatch.monitoring.MonitoringProperties.TargetPolicyMode;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TargetValidatorTest {

    private final TargetValidator allowlistOnly = new TargetValidator(new TargetPolicy(
            TargetPolicyMode.ALLOWLIST_ONLY, List.of("localhost", "demo-*", "api.example.com"), List.of(80, 443, 9101)));

    private final TargetValidator publicMode = new TargetValidator(new TargetPolicy(
            TargetPolicyMode.PUBLIC_AND_ALLOWLIST, List.of("localhost"), List.of()));

    @Test
    void acceptsAllowlistedHostAndPort() {
        URI uri = allowlistOnly.validateSyntax("http://localhost:9101/health");
        assertThat(uri.getHost()).isEqualTo("localhost");
    }

    @Test
    void acceptsGlobPattern() {
        assertThat(allowlistOnly.isAllowlisted("demo-healthy")).isTrue();
        assertThat(allowlistOnly.isAllowlisted("demo.healthy")).isFalse();
        assertThat(allowlistOnly.isAllowlisted("evil-demo-healthy")).isFalse();
    }

    @Test
    @DisplayName("host fora da allowlist é recusado no modo ALLOWLIST_ONLY")
    void rejectsNonAllowlistedHost() {
        assertThatThrownBy(() -> allowlistOnly.validateSyntax("https://example.org/health"))
                .isInstanceOf(TargetRejectedException.class)
                .extracting("code").isEqualTo("target_not_allowlisted");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://localhost/x", "file:///etc/passwd", "gopher://localhost", "localhost:9101/health"})
    void rejectsNonHttpSchemes(String url) {
        assertThatThrownBy(() -> allowlistOnly.validateSyntax(url))
                .isInstanceOf(TargetRejectedException.class);
    }

    @Test
    void rejectsCredentialsInUrl() {
        assertThatThrownBy(() -> allowlistOnly.validateSyntax("http://user:pass@localhost:9101/"))
                .isInstanceOf(TargetRejectedException.class)
                .extracting("code").isEqualTo("target_userinfo");
    }

    @Test
    void rejectsPortOutsidePolicy() {
        assertThatThrownBy(() -> allowlistOnly.validateSyntax("http://localhost:2222/"))
                .isInstanceOf(TargetRejectedException.class)
                .extracting("code").isEqualTo("target_port");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data",   // metadados de nuvem
            "http://10.0.0.5/",                           // rede privada
            "http://192.168.1.1/",                        // rede privada
            "http://172.16.0.1/",                         // rede privada
            "http://127.0.0.1/",                          // loopback
            "http://0.0.0.0/",                            // any
            "http://100.64.0.1/",                         // CGNAT
            "http://[::1]/",                              // loopback IPv6
            "http://[fd00::1]/",                          // ULA IPv6
            "http://[::ffff:10.0.0.1]/"                   // IPv4 mapeado
    })
    @DisplayName("endereços internos e de metadados são bloqueados mesmo no modo público")
    void blocksInternalAddressesInPublicMode(String url) {
        assertThatThrownBy(() -> publicMode.validateSyntax(url))
                .isInstanceOf(TargetRejectedException.class)
                .extracting("code").isEqualTo("target_private_address");
    }

    @Test
    void publicModeAcceptsPublicLiteral() {
        URI uri = publicMode.validateSyntax("https://93.184.216.34/");
        assertThat(uri.getHost()).isEqualTo("93.184.216.34");
    }

    @Test
    @DisplayName("host na allowlist pode resolver para endereço privado (serviços Docker autorizados)")
    void allowlistedHostMayResolveToPrivateAddress() {
        InetAddress address = allowlistOnly.resolveAndValidate(URI.create("http://localhost:9101/health"));
        assertThat(address.isLoopbackAddress()).isTrue();
    }

    @Test
    void publicModeRejectsHostResolvingToLoopback() {
        TargetValidator strict = new TargetValidator(new TargetPolicy(TargetPolicyMode.PUBLIC_AND_ALLOWLIST, List.of(), List.of()));
        assertThatThrownBy(() -> strict.resolveAndValidate(URI.create("http://localhost/")))
                .isInstanceOf(TargetRejectedException.class)
                .extracting("code").isEqualTo("target_private_address");
    }

    @Test
    void classifiesAddressesCorrectly() throws Exception {
        assertThat(TargetValidator.isDisallowedAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(TargetValidator.isDisallowedAddress(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(TargetValidator.isDisallowedAddress(InetAddress.getByName("198.18.0.1"))).isTrue();
        assertThat(TargetValidator.isDisallowedAddress(InetAddress.getByName("240.0.0.1"))).isTrue();
    }
}
