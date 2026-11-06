-- InfraWatch — Fase 4: incidentes, comentários, histórico de incidentes e manutenções programadas

ALTER TABLE monitored_resources
    ADD COLUMN auto_incident BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN status_before_maintenance VARCHAR(20);

CREATE TABLE incidents (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    resource_id      UUID REFERENCES monitored_resources (id) ON DELETE SET NULL,
    alert_event_id   UUID REFERENCES alert_events (id) ON DELETE SET NULL,
    number           BIGINT       NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      VARCHAR(4000),
    severity         VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    origin           VARCHAR(20)  NOT NULL,
    assignee_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    opened_at        TIMESTAMPTZ  NOT NULL,
    acknowledged_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    cause            VARCHAR(2000),
    notes            VARCHAR(4000),
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_incidents_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_incidents_severity CHECK (severity IN ('INFO', 'WARNING', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incidents_origin CHECK (origin IN ('AUTOMATIC', 'MANUAL')),
    CONSTRAINT ux_incidents_number UNIQUE (organization_id, number)
);
CREATE INDEX ix_incidents_org_status ON incidents (organization_id, status, opened_at DESC);
CREATE INDEX ix_incidents_resource ON incidents (resource_id, opened_at DESC);
-- Um único incidente automático ativo por recurso
CREATE UNIQUE INDEX ux_incidents_auto_active ON incidents (resource_id)
    WHERE origin = 'AUTOMATIC' AND status NOT IN ('RESOLVED', 'CLOSED');

CREATE TABLE incident_comments (
    id           UUID PRIMARY KEY,
    incident_id  UUID         NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    body         VARCHAR(4000) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_incident_comments_incident ON incident_comments (incident_id, created_at);

CREATE TABLE incident_history (
    id           UUID PRIMARY KEY,
    incident_id  UUID         NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    event_type   VARCHAR(40)  NOT NULL,
    from_value   VARCHAR(200),
    to_value     VARCHAR(200),
    details      VARCHAR(1000),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_incident_history_incident ON incident_history (incident_id, created_at);

CREATE TABLE maintenance_windows (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    reason           VARCHAR(2000),
    starts_at        TIMESTAMPTZ  NOT NULL,
    ends_at          TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    responsible_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    activated_at     TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    finish_reason    VARCHAR(30),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_mw_status CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_mw_period CHECK (ends_at > starts_at)
);
CREATE INDEX ix_mw_org_status ON maintenance_windows (organization_id, status, starts_at);
CREATE INDEX ix_mw_due ON maintenance_windows (status, starts_at, ends_at);

CREATE TABLE maintenance_window_resources (
    window_id    UUID NOT NULL REFERENCES maintenance_windows (id) ON DELETE CASCADE,
    resource_id  UUID NOT NULL REFERENCES monitored_resources (id) ON DELETE CASCADE,
    PRIMARY KEY (window_id, resource_id)
);
CREATE INDEX ix_mwr_resource ON maintenance_window_resources (resource_id);
