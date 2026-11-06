package com.infrawatch.integrations;

import com.infrawatch.alerts.AlertEnums.Severity;
import com.infrawatch.audit.AuditService;
import com.infrawatch.common.ApiException;
import com.infrawatch.integrations.IntegrationDtos.ChannelRequest;
import com.infrawatch.integrations.IntegrationDtos.ChannelResponse;
import com.infrawatch.integrations.IntegrationDtos.DeliveryResponse;
import com.infrawatch.monitoring.TargetRejectedException;
import com.infrawatch.monitoring.TargetValidator;
import com.infrawatch.organization.MemberRole;
import com.infrawatch.security.OrganizationContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChannelService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final NotificationChannelRepository channels;
    private final NotificationDeliveryRepository deliveries;
    private final TargetValidator targetValidator;
    private final DeliveryDispatcher dispatcher;
    private final IntegrationsProperties properties;
    private final AuditService audit;

    public ChannelService(NotificationChannelRepository channels, NotificationDeliveryRepository deliveries,
                          TargetValidator targetValidator, DeliveryDispatcher dispatcher, IntegrationsProperties properties,
                          AuditService audit) {
        this.channels = channels;
        this.deliveries = deliveries;
        this.targetValidator = targetValidator;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ChannelResponse> list(OrganizationContext ctx) {
        ctx.require(MemberRole.ADMIN);
        return channels.findByOrganizationIdOrderByCreatedAtAsc(ctx.organizationId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ChannelResponse create(OrganizationContext ctx, ChannelRequest req) {
        ctx.require(MemberRole.ADMIN);
        NotificationChannel c = new NotificationChannel(ctx.organizationId(), req.name().trim(), req.type(), ctx.userId());
        apply(c, req);
        channels.save(c);
        audit.record(ctx.organizationId(), ctx.userId(), "channel.created", "notification_channel", c.getId(),
                "Canal \"" + c.getName() + "\" (" + c.getType() + ") criado");
        return toResponse(c);
    }

    @Transactional
    public ChannelResponse update(OrganizationContext ctx, UUID id, ChannelRequest req) {
        ctx.require(MemberRole.ADMIN);
        NotificationChannel c = require(ctx, id);
        if (req.name() != null) c.setName(req.name().trim());
        apply(c, req);
        audit.record(ctx.organizationId(), ctx.userId(), "channel.updated", "notification_channel", id, "Canal \"" + c.getName() + "\" atualizado");
        return toResponse(c);
    }

    @Transactional
    public void delete(OrganizationContext ctx, UUID id) {
        ctx.require(MemberRole.ADMIN);
        NotificationChannel c = require(ctx, id);
        channels.delete(c);
        audit.record(ctx.organizationId(), ctx.userId(), "channel.deleted", "notification_channel", id, "Canal \"" + c.getName() + "\" removido");
    }

    /** Envia uma mensagem de teste imediatamente (respeita dispatch-enabled). */
    @Transactional
    public DeliveryResponse test(OrganizationContext ctx, UUID id) {
        ctx.require(MemberRole.ADMIN);
        NotificationChannel c = require(ctx, id);
        String payload = WebhookPayload.json(ctx.organizationId(), "test", Severity.INFO, "Teste de canal: " + c.getName(),
                "Mensagem de teste enviada pelo InfraWatch.", properties.baseUrl() + "/configuracoes", null, Instant.now());
        NotificationDelivery d = new NotificationDelivery(c.getId(), ctx.organizationId(), "test", Severity.INFO,
                "Teste de canal: " + c.getName(), "Mensagem de teste enviada pelo InfraWatch.", "/configuracoes", null, null, payload);
        if (!properties.dispatchEnabled()) {
            d.skipped("Envio externo desativado (infrawatch.integrations.dispatch-enabled=false)");
        } else {
            DeliveryDispatcher.Result r = dispatcher.send(c, d);
            if (r.ok()) d.sent(Instant.now(), r.code()); else d.failed(Instant.now(), r.error(), r.code(), 1);
            c.recordDelivery(Instant.now(), r.ok() ? null : r.error());
        }
        deliveries.save(d);
        return DeliveryResponse.of(d, c.getName());
    }

    @Transactional(readOnly = true)
    public Page<DeliveryResponse> deliveries(OrganizationContext ctx, UUID channelId, Pageable pageable) {
        ctx.require(MemberRole.ADMIN);
        Page<NotificationDelivery> page = channelId == null
                ? deliveries.findByOrganizationIdOrderByCreatedAtDesc(ctx.organizationId(), pageable)
                : deliveries.findByOrganizationIdAndChannelIdOrderByCreatedAtDesc(ctx.organizationId(), channelId, pageable);
        var names = channels.findByOrganizationIdOrderByCreatedAtAsc(ctx.organizationId()).stream()
                .collect(java.util.stream.Collectors.toMap(NotificationChannel::getId, NotificationChannel::getName));
        return page.map(d -> DeliveryResponse.of(d, names.getOrDefault(d.getChannelId(), "Canal removido")));
    }

    private void apply(NotificationChannel c, ChannelRequest req) {
        if (req.enabled() != null) c.setEnabled(req.enabled());
        if (req.minSeverity() != null) c.setMinSeverity(req.minSeverity());
        if (req.eventTypes() != null) c.setEventTypes(req.eventTypes().isEmpty() ? null : String.join(",", req.eventTypes()));
        if (c.getType() == NotificationChannel.Type.EMAIL) {
            if (req.emailRecipients() != null) {
                List<String> list = req.emailRecipients().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (list.isEmpty()) throw ApiException.badRequest("channel_recipients", "Informe ao menos um destinatário.");
                for (String e : list) if (!EMAIL.matcher(e).matches()) throw ApiException.badRequest("channel_recipients", "E-mail inválido: " + e);
                c.setEmailRecipients(String.join(",", list));
            } else if (c.getEmailRecipients() == null) {
                throw ApiException.badRequest("channel_recipients", "Informe ao menos um destinatário.");
            }
        } else {
            if (req.webhookUrl() != null) {
                try {
                    var uri = targetValidator.validateSyntax(req.webhookUrl());
                    c.setWebhookUrl(uri.toString());
                } catch (TargetRejectedException e) {
                    throw ApiException.badRequest(e.getCode(), "Destino do webhook recusado pela política: " + e.getMessage());
                }
            } else if (c.getWebhookUrl() == null) {
                throw ApiException.badRequest("channel_url", "Informe a URL do webhook.");
            }
            if (req.webhookSecret() != null) c.setWebhookSecret(req.webhookSecret().isBlank() ? null : req.webhookSecret());
        }
    }

    private NotificationChannel require(OrganizationContext ctx, UUID id) {
        return channels.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Canal não encontrado."));
    }

    private ChannelResponse toResponse(NotificationChannel c) {
        return new ChannelResponse(c.getId(), c.getName(), c.getType(), c.isEnabled(),
                c.getEmailRecipients() == null ? List.of() : List.of(c.getEmailRecipients().split(",")),
                c.getWebhookUrl(), c.getWebhookSecret() != null && !c.getWebhookSecret().isBlank(), c.getMinSeverity(),
                c.getMinSeverity().getLabel(), List.copyOf(c.eventTypeSet()), c.getLastDeliveryAt(), c.getLastError(),
                c.getCreatedAt(), properties.dispatchEnabled());
    }
}
