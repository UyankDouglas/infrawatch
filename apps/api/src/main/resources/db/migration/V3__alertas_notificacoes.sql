-- InfraWatch — Fase 3: regras de alerta, eventos de alerta, estado de condição e notificações internas

CREATE TABLE alert_rules (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    description      VARCHAR(500),
    metric           VARCHAR(30)  NOT NULL,
    operator         VARCHAR(5),
    threshold        NUMERIC(12,2),
    duration_seconds INTEGER      NOT NULL DEFAULT 0,
    occurrences      INTEGER      NOT NULL DEFAULT 1,
    severity         VARCHAR(20)  NOT NULL,
    resource_id      UUID REFERENCES monitored_resources (id) ON DELETE CASCADE,
    group_id         UUID REFERENCES resource_groups (id) ON DELETE CASCADE,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_alert_rules_metric CHECK (metric IN ('STATUS_DOWN', 'STATUS_DEGRADED', 'LATENCY', 'CPU', 'MEMORY', 'DISK', 'AGENT_NO_DATA')),
    CONSTRAINT ck_alert_rules_operator CHECK (operator IS NULL OR operator IN ('GT', 'GTE', 'LT', 'LTE')),
    CONSTRAINT ck_alert_rules_severity CHECK (severity IN ('INFO', 'WARNING', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_alert_rules_occurrences CHECK (occurrences BETWEEN 1 AND 100),
    CONSTRAINT ck_alert_rules_duration CHECK (duration_seconds BETWEEN 0 AND 86400)
);
CREATE INDEX ix_alert_rules_org ON alert_rules (organization_id, enabled);

CREATE TABLE alert_events (
    id                 UUID PRIMARY KEY,
    organization_id    UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    rule_id            UUID REFERENCES alert_rules (id) ON DELETE SET NULL,
    resource_id        UUID REFERENCES monitored_resources (id) ON DELETE CASCADE,
    metric             VARCHAR(30)  NOT NULL,
    severity           VARCHAR(20)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    title              VARCHAR(200) NOT NULL,
    message            VARCHAR(1000),
    value              NUMERIC(12,2),
    threshold          NUMERIC(12,2),
    triggered_at       TIMESTAMPTZ  NOT NULL,
    last_evaluated_at  TIMESTAMPTZ  NOT NULL,
    acknowledged_at    TIMESTAMPTZ,
    acknowledged_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    acknowledge_note   VARCHAR(500),
    resolved_at        TIMESTAMPTZ,
    resolved_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    resolution         VARCHAR(30),
    resolution_note    VARCHAR(500),
    occurrence_count   INTEGER      NOT NULL DEFAULT 1,
    notification_count INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_alert_events_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    CONSTRAINT ck_alert_events_severity CHECK (severity IN ('INFO', 'WARNING', 'HIGH', 'CRITICAL'))
);
CREATE INDEX ix_alert_events_org_status ON alert_events (organization_id, status, triggered_at DESC);
CREATE INDEX ix_alert_events_resource ON alert_events (resource_id, triggered_at DESC);
-- Supressão de duplicados: no máximo um alerta não resolvido por regra+recurso
CREATE UNIQUE INDEX ux_alert_events_active ON alert_events (rule_id, resource_id) WHERE status <> 'RESOLVED';

-- Estado de condições numéricas (ocorrências consecutivas / duração mínima) por regra+recurso
CREATE TABLE alert_condition_states (
    rule_id         UUID NOT NULL REFERENCES alert_rules (id) ON DELETE CASCADE,
    resource_id     UUID NOT NULL REFERENCES monitored_resources (id) ON DELETE CASCADE,
    breaching_since TIMESTAMPTZ,
    occurrences     INTEGER NOT NULL DEFAULT 0,
    last_value      NUMERIC(12,2),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (rule_id, resource_id)
);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type            VARCHAR(40)  NOT NULL,
    severity        VARCHAR(20)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    message         VARCHAR(1000),
    link            VARCHAR(300),
    resource_id     UUID REFERENCES monitored_resources (id) ON DELETE SET NULL,
    alert_event_id  UUID REFERENCES alert_events (id) ON DELETE SET NULL,
    dedupe_key      VARCHAR(200),
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, read_at, created_at DESC);
CREATE INDEX ix_notifications_org ON notifications (organization_id, created_at DESC);
-- Outbox: evita duplicidade quando o mesmo fato gera a mesma notificação (ex.: reprocessamento)
CREATE UNIQUE INDEX ux_notifications_dedupe ON notifications (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
