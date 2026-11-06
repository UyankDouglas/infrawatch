-- InfraWatch — Fase 2: agentes autorizados, amostras de métricas e agregações

CREATE TABLE agents (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    resource_id      UUID         NOT NULL UNIQUE REFERENCES monitored_resources (id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    interval_seconds INTEGER      NOT NULL DEFAULT 30,
    hostname         VARCHAR(255),
    agent_version    VARCHAR(40),
    last_seen_at     TIMESTAMPTZ,
    last_sample_at   TIMESTAMPTZ,
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_agents_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_agents_interval CHECK (interval_seconds BETWEEN 10 AND 3600)
);
CREATE INDEX ix_agents_org ON agents (organization_id);

CREATE TABLE agent_credentials (
    id            UUID PRIMARY KEY,
    agent_id      UUID        NOT NULL REFERENCES agents (id) ON DELETE CASCADE,
    token_prefix  VARCHAR(16) NOT NULL,
    token_hash    VARCHAR(64) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at    TIMESTAMPTZ,
    last_used_at  TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_agent_credentials_prefix ON agent_credentials (token_prefix);
CREATE INDEX ix_agent_credentials_agent ON agent_credentials (agent_id);

-- Amostras brutas (retenção curta, configurável)
CREATE TABLE metric_samples (
    id               BIGSERIAL PRIMARY KEY,
    resource_id      UUID        NOT NULL REFERENCES monitored_resources (id) ON DELETE CASCADE,
    organization_id  UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    agent_id         UUID        NOT NULL REFERENCES agents (id) ON DELETE CASCADE,
    sampled_at       TIMESTAMPTZ NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    cpu_percent      NUMERIC(5,2),
    load_avg_1m      NUMERIC(8,2),
    mem_total_bytes  BIGINT,
    mem_used_bytes   BIGINT,
    mem_percent      NUMERIC(5,2),
    disk_path        VARCHAR(255),
    disk_total_bytes BIGINT,
    disk_used_bytes  BIGINT,
    disk_percent     NUMERIC(5,2),
    uptime_seconds   BIGINT,
    CONSTRAINT ck_metric_samples_cpu CHECK (cpu_percent IS NULL OR (cpu_percent >= 0 AND cpu_percent <= 100)),
    CONSTRAINT ck_metric_samples_mem CHECK (mem_percent IS NULL OR (mem_percent >= 0 AND mem_percent <= 100)),
    CONSTRAINT ck_metric_samples_disk CHECK (disk_percent IS NULL OR (disk_percent >= 0 AND disk_percent <= 100))
);
CREATE INDEX ix_metric_samples_resource_time ON metric_samples (resource_id, sampled_at DESC);
CREATE INDEX ix_metric_samples_sampled ON metric_samples (sampled_at);

-- Agregações por intervalo (5 min e 1 h), retenção longa
CREATE TABLE metric_rollups (
    id                BIGSERIAL PRIMARY KEY,
    resource_id       UUID        NOT NULL REFERENCES monitored_resources (id) ON DELETE CASCADE,
    organization_id   UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    bucket_seconds    INTEGER     NOT NULL,
    bucket_start      TIMESTAMPTZ NOT NULL,
    samples           INTEGER     NOT NULL,
    cpu_avg           NUMERIC(5,2),
    cpu_max           NUMERIC(5,2),
    mem_percent_avg   NUMERIC(5,2),
    mem_percent_max   NUMERIC(5,2),
    mem_used_avg      BIGINT,
    disk_percent_avg  NUMERIC(5,2),
    disk_percent_max  NUMERIC(5,2),
    disk_used_avg     BIGINT,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_metric_rollups UNIQUE (resource_id, bucket_seconds, bucket_start)
);
CREATE INDEX ix_metric_rollups_resource ON metric_rollups (resource_id, bucket_seconds, bucket_start DESC);
CREATE INDEX ix_metric_rollups_bucket ON metric_rollups (bucket_start);
