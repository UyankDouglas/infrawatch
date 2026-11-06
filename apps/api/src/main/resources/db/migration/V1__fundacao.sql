-- InfraWatch — Fase 1: fundação (usuários, organizações, recursos, verificações, histórico, auditoria)

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (email);

CREATE TABLE organizations (
    id          UUID PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(80)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_organizations_slug ON organizations (slug);

CREATE TABLE organization_members (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_org_members UNIQUE (organization_id, user_id),
    CONSTRAINT ck_org_members_role CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER'))
);
CREATE INDEX ix_org_members_user ON organization_members (user_id);

CREATE TABLE resource_groups (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_resource_groups_name UNIQUE (organization_id, name)
);

CREATE TABLE monitored_resources (
    id                     UUID PRIMARY KEY,
    organization_id        UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    group_id               UUID         REFERENCES resource_groups (id) ON DELETE SET NULL,
    name                   VARCHAR(120) NOT NULL,
    description            VARCHAR(1000),
    type                   VARCHAR(30)  NOT NULL,
    target_url             VARCHAR(2048) NOT NULL,
    environment            VARCHAR(20)  NOT NULL,
    owner                  VARCHAR(120),
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    current_status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    status_since           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_check_at          TIMESTAMPTZ,
    last_success           BOOLEAN,
    last_response_time_ms  INTEGER,
    last_http_status       INTEGER,
    last_error_code        VARCHAR(60),
    consecutive_failures   INTEGER      NOT NULL DEFAULT 0,
    consecutive_successes  INTEGER      NOT NULL DEFAULT 0,
    consecutive_slow       INTEGER      NOT NULL DEFAULT 0,
    next_check_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_evaluated_at      TIMESTAMPTZ,
    version                BIGINT       NOT NULL DEFAULT 0,
    created_by             UUID         REFERENCES users (id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_monitored_resources_name UNIQUE (organization_id, name),
    CONSTRAINT ck_monitored_resources_type CHECK (type IN ('HTTP', 'HTTPS', 'HEALTH_ENDPOINT', 'AGENT_SERVER')),
    CONSTRAINT ck_monitored_resources_env CHECK (environment IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION')),
    CONSTRAINT ck_monitored_resources_status CHECK (current_status IN ('PENDING', 'UP', 'DOWN', 'DEGRADED', 'MAINTENANCE', 'UNKNOWN'))
);
CREATE INDEX ix_monitored_resources_org ON monitored_resources (organization_id);
CREATE INDEX ix_monitored_resources_due ON monitored_resources (next_check_at) WHERE enabled = TRUE;
CREATE INDEX ix_monitored_resources_status ON monitored_resources (organization_id, current_status);

CREATE TABLE monitored_resource_tags (
    resource_id UUID        NOT NULL REFERENCES monitored_resources (id) ON DELETE CASCADE,
    tag         VARCHAR(40) NOT NULL,
    PRIMARY KEY (resource_id, tag)
);

CREATE TABLE health_check_configs (
    id                    UUID PRIMARY KEY,
    resource_id           UUID         NOT NULL UNIQUE REFERENCES monitored_resources (id) ON DELETE CASCADE,
    interval_seconds      INTEGER      NOT NULL DEFAULT 60,
    timeout_ms            INTEGER      NOT NULL DEFAULT 5000,
    http_method           VARCHAR(10)  NOT NULL DEFAULT 'GET',
    expected_status_codes VARCHAR(120) NOT NULL DEFAULT '200-299',
    degraded_latency_ms   INTEGER,
    failure_threshold     INTEGER      NOT NULL DEFAULT 3,
    recovery_threshold    INTEGER      NOT NULL DEFAULT 2,
    follow_redirects      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_hcc_interval CHECK (interval_seconds >= 1),
    CONSTRAINT ck_hcc_timeout CHECK (timeout_ms BETWEEN 100 AND 60000),
    CONSTRAINT ck_hcc_thresholds CHECK (failure_threshold >= 1 AND recovery_threshold >= 1)
);

CREATE TABLE health_check_executions (
    id               UUID PRIMARY KEY,
    resource_id      UUID        NOT NULL REFERENCES monitored_resources (id) ON DELETE CASCADE,
    organization_id  UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    run_id           UUID        NOT NULL,
    check_type       VARCHAR(30) NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ NOT NULL,
    duration_ms      INTEGER     NOT NULL,
    success          BOOLEAN     NOT NULL,
    http_status      INTEGER,
    response_time_ms INTEGER,
    error_code       VARCHAR(60),
    error_message    VARCHAR(500),
    resolved_address VARCHAR(80),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_hce_resource_started ON health_check_executions (resource_id, started_at DESC);
CREATE INDEX ix_hce_org_started ON health_check_executions (organization_id, started_at DESC);

CREATE TABLE resource_status_history (
    id              UUID PRIMARY KEY,
    resource_id     UUID        NOT NULL REFERENCES monitored_resources (id) ON DELETE CASCADE,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    from_status     VARCHAR(20) NOT NULL,
    to_status       VARCHAR(20) NOT NULL,
    reason          VARCHAR(300),
    execution_id    UUID,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_rsh_resource_changed ON resource_status_history (resource_id, changed_at DESC);
CREATE INDEX ix_rsh_org_changed ON resource_status_history (organization_id, changed_at DESC);

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations (id) ON DELETE SET NULL,
    user_id         UUID REFERENCES users (id) ON DELETE SET NULL,
    action          VARCHAR(60)  NOT NULL,
    entity_type     VARCHAR(60),
    entity_id       UUID,
    details         VARCHAR(2000),
    ip_address      VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_org_created ON audit_logs (organization_id, created_at DESC);
