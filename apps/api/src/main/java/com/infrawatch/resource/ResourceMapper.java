package com.infrawatch.resource;

import com.infrawatch.resource.ResourceDtos.CheckConfigResponse;
import com.infrawatch.resource.ResourceDtos.GroupRef;
import com.infrawatch.resource.ResourceDtos.ResourceResponse;
import java.util.List;

public final class ResourceMapper {
    private ResourceMapper() {}

    public static ResourceResponse toResponse(MonitoredResource r) {
        HealthCheckConfig c = r.getCheckConfig();
        CheckConfigResponse check = c == null ? null : new CheckConfigResponse(
                c.getIntervalSeconds(), c.getTimeoutMs(), c.getHttpMethod(), c.getExpectedStatusCodes(),
                c.getDegradedLatencyMs(), c.getFailureThreshold(), c.getRecoveryThreshold(), c.isFollowRedirects());
        GroupRef group = r.getGroup() == null ? null : new GroupRef(r.getGroup().getId(), r.getGroup().getName());
        return new ResourceResponse(
                r.getId(), r.getName(), r.getDescription(), r.getType(), r.getType().getLabel(), r.getTargetUrl(),
                r.getEnvironment(), r.getEnvironment().getLabel(), group, r.getOwner(), List.copyOf(r.getTags()),
                r.isEnabled(), r.getCurrentStatus(), r.getCurrentStatus().getLabel(), r.getStatusSince(),
                r.getLastCheckAt(), r.getLastSuccess(), r.getLastResponseTimeMs(), r.getLastHttpStatus(),
                r.getLastErrorCode(), r.getConsecutiveFailures(), r.getConsecutiveSuccesses(), r.getNextCheckAt(),
                check, r.getCreatedAt(), r.getUpdatedAt());
    }
}
