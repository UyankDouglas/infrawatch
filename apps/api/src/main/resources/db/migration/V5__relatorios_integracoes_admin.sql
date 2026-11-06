-- InfraWatch — Fase 5: exportações, canais externos (outbox), convites, recuperação de senha, sessões JDBC

CREATE TABLE report_exports (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id          UUID REFERENCES users (id) ON DELETE SET NULL,
    report_type      VARCHAR(30)  NOT NULL,
    format           VARCHAR(10)  NOT NULL,
    period           VARCHAR(10)  NOT NULL,
    maintenance_mode VARCHAR(20)  NOT NULL,
    filters          VARCHAR(500),
    file_name        VARCHAR(200) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    row_count        INTEGER      NOT NULL,
    content          BYTEA        NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_report_exports_type CHECK (report_type IN ('AVAILABILITY', 'INCIDENTS', 'CHECKS', 'ALERTS')),
    CONSTRAINT ck_report_exports_format CHECK (format IN ('CSV', 'XLSX'))
);
CREATE INDEX ix_report_exports_org ON report_exports (organization_id, created_at DESC);

CREATE TABLE notification_channels (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name              VARCHAR(120) NOT NULL,
    type              VARCHAR(20)  NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    email_recipients  VARCHAR(1000),
    webhook_url       VARCHAR(2048),
    webhook_secret    VARCHAR(200),
    min_severity      VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    event_types       VARCHAR(500),
    created_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    last_delivery_at  TIMESTAMPTZ,
    last_error        VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_channels_type CHECK (type IN ('EMAIL', 'WEBHOOK')),
    CONSTRAINT ck_channels_severity CHECK (min_severity IN ('INFO', 'WARNING', 'HIGH', 'CRITICAL'))
);
CREATE INDEX ix_channels_org ON notification_channels (organization_id, enabled);

-- Outbox de entregas externas: cada fato gera no máximo uma entrega por canal (dedupe_key)
CREATE TABLE notification_deliveries (
    id               UUID PRIMARY KEY,
    channel_id       UUID         NOT NULL REFERENCES notification_channels (id) ON DELETE CASCADE,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    event_type       VARCHAR(40)  NOT NULL,
    severity         VARCHAR(20)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    message          VARCHAR(1000),
    link             VARCHAR(300),
    resource_id      UUID,
    dedupe_key       VARCHAR(200),
    payload          TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts         INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error       VARCHAR(500),
    sent_at          TIMESTAMPTZ,
    response_code    INTEGER,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_deliveries_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED', 'EXPIRED'))
);
CREATE INDEX ix_deliveries_due ON notification_deliveries (status, next_attempt_at);
CREATE INDEX ix_deliveries_org ON notification_deliveries (organization_id, created_at DESC);
CREATE UNIQUE INDEX ux_deliveries_dedupe ON notification_deliveries (channel_id, dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE TABLE organization_invites (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    email            VARCHAR(255) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL,
    invited_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    accepted_at      TIMESTAMPTZ,
    accepted_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    revoked_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_invites_role CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER'))
);
CREATE UNIQUE INDEX ux_invites_token ON organization_invites (token_hash);
CREATE INDEX ix_invites_org ON organization_invites (organization_id, created_at DESC);

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_password_reset_token ON password_reset_tokens (token_hash);
CREATE INDEX ix_password_reset_user ON password_reset_tokens (user_id);

-- Spring Session JDBC (esquema oficial para PostgreSQL)
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);
