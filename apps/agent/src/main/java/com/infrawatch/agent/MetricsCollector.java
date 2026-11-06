package com.infrawatch.agent;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/**
 * Coleta somente métricas permitidas do host, usando APIs padrão da JVM (ciente de cgroups em containers).
 * Métricas indisponíveis são enviadas como {@code null} — o servidor as exibe como "indisponível", nunca inventa valores.
 */
final class MetricsCollector {

    private final Path diskPath;
    private final com.sun.management.OperatingSystemMXBean os;

    MetricsCollector(String diskPath) {
        this.diskPath = Path.of(diskPath);
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        this.os = bean instanceof com.sun.management.OperatingSystemMXBean sun ? sun : null;
    }

    /** Monta o JSON da amostra sem dependências externas. */
    String sample(String hostname, String version) {
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        field(json, "sampledAt", quote(Instant.now().toString()));
        field(json, "cpuPercent", cpuPercent());
        field(json, "loadAvg1m", loadAverage());
        json.append("\"memory\":").append(memory()).append(',');
        json.append("\"disk\":").append(disk()).append(',');
        field(json, "uptimeSeconds", uptimeSeconds());
        field(json, "hostname", quote(hostname));
        json.append("\"agentVersion\":").append(quote(version));
        json.append('}');
        return json.toString();
    }

    private String cpuPercent() {
        if (os == null) return "null";
        double load = os.getCpuLoad(); // carga do sistema (0..1) ou negativa se indisponível
        if (load < 0 || Double.isNaN(load)) return "null";
        return String.format(Locale.ROOT, "%.2f", Math.min(100.0, load * 100.0));
    }

    private String loadAverage() {
        double avg = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        return avg < 0 ? "null" : String.format(Locale.ROOT, "%.2f", avg);
    }

    private String memory() {
        if (os == null) return "null";
        long total = os.getTotalMemorySize();
        long free = os.getFreeMemorySize();
        if (total <= 0 || free < 0) return "null";
        return "{\"totalBytes\":" + total + ",\"usedBytes\":" + (total - free) + "}";
    }

    private String disk() {
        try {
            FileStore store = Files.getFileStore(diskPath);
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            if (total <= 0) return "null";
            return "{\"path\":" + quote(diskPath.toString()) + ",\"totalBytes\":" + total + ",\"usedBytes\":" + (total - usable) + "}";
        } catch (IOException | UnsupportedOperationException e) {
            return "null";
        }
    }

    /** Uptime do sistema operacional via /proc/uptime (Linux). Em outros sistemas, indisponível. */
    private String uptimeSeconds() {
        Path proc = Path.of("/proc/uptime");
        if (!Files.isReadable(proc)) return "null";
        try {
            String first = Files.readString(proc).trim().split("\\s+")[0];
            return String.valueOf((long) Double.parseDouble(first));
        } catch (IOException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return "null";
        }
    }

    static String defaultHostname() {
        String env = System.getenv("HOSTNAME");
        if (env != null && !env.isBlank()) return env;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "desconhecido";
        }
    }

    private static void field(StringBuilder json, String name, String rawValue) {
        json.append('"').append(name).append("\":").append(rawValue).append(',');
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
