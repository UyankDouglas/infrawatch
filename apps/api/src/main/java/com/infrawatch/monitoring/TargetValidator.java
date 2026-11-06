package com.infrawatch.monitoring;

import com.infrawatch.monitoring.MonitoringProperties.TargetPolicy;
import com.infrawatch.monitoring.MonitoringProperties.TargetPolicyMode;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Política de alvos e proteção contra SSRF.
 *
 * Regras:
 *  - Apenas http/https, sem credenciais na URL, host obrigatório.
 *  - Porta restrita à lista configurada (vazia = qualquer porta).
 *  - Hosts na allowlist são aceitos mesmo que resolvam para redes privadas (ex.: serviços Docker da demo),
 *    pois foram autorizados explicitamente pelo operador.
 *  - Demais hosts só são aceitos no modo PUBLIC_AND_ALLOWLIST e nunca quando resolvem para loopback,
 *    link-local, redes privadas, multicast, endereços de metadados de nuvem ou "any".
 *  - A resolução DNS é feita antes da requisição e o endereço efetivo é registrado na execução.
 */
@Component
public class TargetValidator {

    private static final Pattern HOSTNAME = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?)*\\.?$");

    private final TargetPolicy policy;
    private final List<Pattern> allowedHostPatterns;

    @org.springframework.beans.factory.annotation.Autowired
    public TargetValidator(MonitoringProperties properties) {
        this(properties.targetPolicy());
    }

    public TargetValidator(TargetPolicy policy) {
        this.policy = policy;
        this.allowedHostPatterns = policy.allowedHosts().stream()
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .map(TargetValidator::globToPattern)
                .toList();
    }

    /** Validação sintática + política de host/porta. Não resolve DNS (usado no cadastro). */
    public URI validateSyntax(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new TargetRejectedException("target_invalid", "Informe uma URL.");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new TargetRejectedException("target_invalid", "URL malformada.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new TargetRejectedException("target_scheme", "Somente http e https são permitidos.");
        }
        if (uri.getRawUserInfo() != null) {
            throw new TargetRejectedException("target_userinfo", "Credenciais na URL não são permitidas.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new TargetRejectedException("target_host", "A URL precisa de um host válido.");
        }
        String host = normalizeHost(uri.getHost());
        if (!isIpLiteral(host) && !HOSTNAME.matcher(host).matches()) {
            throw new TargetRejectedException("target_host", "Host inválido.");
        }
        int port = uri.getPort() == -1 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
        if (!policy.allowedPorts().isEmpty() && !policy.allowedPorts().contains(port)) {
            throw new TargetRejectedException("target_port", "Porta " + port + " não autorizada pela política.");
        }
        boolean allowlisted = isAllowlisted(host);
        if (!allowlisted && policy.mode() == TargetPolicyMode.ALLOWLIST_ONLY) {
            throw new TargetRejectedException("target_not_allowlisted",
                    "Host \"" + host + "\" não está na lista de destinos autorizados.");
        }
        if (!allowlisted && isIpLiteral(host)) {
            InetAddress literal = parseLiteral(host);
            if (literal != null && isDisallowedAddress(literal)) {
                throw new TargetRejectedException("target_private_address", "Endereço interno não autorizado.");
            }
        }
        return uri;
    }

    /** Resolve o host e aplica a política ao endereço efetivamente utilizado. */
    public InetAddress resolveAndValidate(URI uri) {
        String host = normalizeHost(uri.getHost());
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new TargetRejectedException("dns_failure", "Não foi possível resolver o host \"" + host + "\".");
        }
        if (addresses.length == 0) {
            throw new TargetRejectedException("dns_failure", "Host sem endereços.");
        }
        boolean allowlisted = isAllowlisted(host);
        if (!allowlisted) {
            if (policy.mode() == TargetPolicyMode.ALLOWLIST_ONLY) {
                throw new TargetRejectedException("target_not_allowlisted", "Host não autorizado.");
            }
            for (InetAddress address : addresses) {
                if (isDisallowedAddress(address)) {
                    throw new TargetRejectedException("target_private_address",
                            "O host resolve para um endereço interno não autorizado (" + address.getHostAddress() + ").");
                }
            }
        }
        return addresses[0];
    }

    public boolean isAllowlisted(String host) {
        String h = normalizeHost(host);
        return allowedHostPatterns.stream().anyMatch(p -> p.matcher(h).matches());
    }

    static boolean isDisallowedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff, second = b[1] & 0xff;
            if (first == 100 && second >= 64 && second <= 127) return true;       // 100.64.0.0/10 (CGNAT)
            if (first == 169 && second == 254) return true;                        // link-local / metadados (169.254.169.254)
            if (first == 0) return true;                                           // 0.0.0.0/8
            if (first == 192 && second == 0 && (b[2] & 0xff) == 0) return true;    // 192.0.0.0/24
            if (first == 198 && (second == 18 || second == 19)) return true;       // 198.18.0.0/15 (benchmark)
            return first >= 240;                                                   // reservado / broadcast
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return true;                               // fc00::/7 (ULA)
            // IPv4 mapeado (::ffff:a.b.c.d)
            boolean mapped = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) { mapped = false; break; }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isDisallowedAddress(InetAddress.getByAddress(Arrays.copyOfRange(b, 12, 16)));
                } catch (UnknownHostException ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        try {
            return IDN.toASCII(h);
        } catch (IllegalArgumentException e) {
            return h;
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") || host.contains(":");
    }

    private static InetAddress parseLiteral(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static Pattern globToPattern(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            if (c == '*') sb.append("[a-z0-9.-]*");
            else sb.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(sb.append("$").toString());
    }
}
