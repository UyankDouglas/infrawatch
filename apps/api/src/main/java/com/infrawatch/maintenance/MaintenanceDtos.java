package com.infrawatch.maintenance;

import com.infrawatch.maintenance.MaintenanceWindow.FinishReason;
import com.infrawatch.maintenance.MaintenanceWindow.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MaintenanceDtos {
    private MaintenanceDtos() {}

    public record CreateRequest(
            @NotBlank(message = "Informe o título.") @Size(max = 200) String title,
            @Size(max = 2000) String reason,
            @NotNull(message = "Informe o início.") Instant startsAt,
            @NotNull(message = "Informe o término.") Instant endsAt,
            @NotEmpty(message = "Selecione ao menos um recurso.") @Size(max = 200) List<UUID> resourceIds,
            UUID responsibleId) {}

    public record PatchRequest(
            @Size(min = 1, max = 200) String title,
            @Size(max = 2000) String reason,
            Instant startsAt,
            Instant endsAt,
            @Size(max = 200) List<UUID> resourceIds,
            UUID responsibleId) {}

    public record ResourceRef(UUID id, String name) {}

    public record UserRef(UUID id, String name) {}

    public record WindowResponse(UUID id, String title, String reason, Instant startsAt, Instant endsAt, Status status,
                                 String statusLabel, List<ResourceRef> resources, UserRef responsible, UserRef createdBy,
                                 Instant activatedAt, Instant finishedAt, FinishReason finishReason, Long durationSeconds,
                                 Instant createdAt) {}
}
