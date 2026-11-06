package com.infrawatch.resource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResourceDtos {
    private ResourceDtos() {}

    public record CheckConfigRequest(
            @Min(value = 30, message = "O intervalo mínimo é 30 segundos.") @Max(value = 86400) Integer intervalSeconds,
            @Min(value = 100) @Max(value = 30000, message = "O timeout máximo é 30000 ms.") Integer timeoutMs,
            @Pattern(regexp = "GET|HEAD", message = "Método deve ser GET ou HEAD.") String httpMethod,
            @Pattern(regexp = "^\\s*\\d{3}(\\s*-\\s*\\d{3})?(\\s*,\\s*\\d{3}(\\s*-\\s*\\d{3})?)*\\s*$",
                    message = "Use códigos ou faixas separados por vírgula, ex.: 200-299,301.") String expectedStatusCodes,
            @Min(value = 1) @Max(value = 60000) Integer degradedLatencyMs,
            @Min(value = 1) @Max(value = 20) Integer failureThreshold,
            @Min(value = 1) @Max(value = 20) Integer recoveryThreshold,
            Boolean followRedirects) {}

    public record CreateResourceRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull(message = "Informe o tipo.") ResourceType type,
            @NotBlank(message = "Informe a URL autorizada.") @Size(max = 2048) String targetUrl,
            @NotNull(message = "Informe o ambiente.") Environment environment,
            UUID groupId,
            @Size(max = 120) String owner,
            @Size(max = 10) List<@Size(min = 1, max = 40) String> tags,
            @Valid CheckConfigRequest check) {}

    public record UpdateResourceRequest(
            @Size(min = 1, max = 120) String name,
            @Size(max = 1000) String description,
            ResourceType type,
            @Size(max = 2048) String targetUrl,
            Environment environment,
            UUID groupId,
            Boolean clearGroup,
            @Size(max = 120) String owner,
            @Size(max = 10) List<@Size(min = 1, max = 40) String> tags,
            Boolean enabled,
            @Valid CheckConfigRequest check) {}

    public record CheckConfigResponse(int intervalSeconds, int timeoutMs, String httpMethod,
                                      String expectedStatusCodes, Integer degradedLatencyMs,
                                      int failureThreshold, int recoveryThreshold, boolean followRedirects) {}

    public record GroupResponse(UUID id, String name, String description, Instant createdAt, long resourceCount) {}

    public record GroupRef(UUID id, String name) {}

    public record ResourceResponse(
            UUID id, String name, String description, ResourceType type, String typeLabel, String targetUrl,
            Environment environment, String environmentLabel, GroupRef group, String owner, List<String> tags,
            boolean enabled, ResourceStatus status, String statusLabel, Instant statusSince,
            Instant lastCheckAt, Boolean lastSuccess, Integer lastResponseTimeMs, Integer lastHttpStatus,
            String lastErrorCode, int consecutiveFailures, int consecutiveSuccesses, Instant nextCheckAt,
            CheckConfigResponse check, Instant createdAt, Instant updatedAt) {}

    public record GroupRequest(@NotBlank(message = "Informe o nome do grupo.") @Size(max = 100) String name,
                               @Size(max = 500) String description) {}
}
