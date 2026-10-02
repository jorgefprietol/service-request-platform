CREATE TABLE service_request (
    id UUID PRIMARY KEY,
    title VARCHAR(120) NOT NULL CHECK (LENGTH(TRIM(title)) >= 3),
    description VARCHAR(2000) NOT NULL CHECK (LENGTH(TRIM(description)) >= 1),
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW','NORMAL','HIGH','CRITICAL')),
    status VARCHAR(15) NOT NULL CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED','CANCELLED')),
    owner VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sla_due_at TIMESTAMP WITH TIME ZONE NOT NULL CHECK (sla_due_at > created_at),
    version BIGINT NOT NULL CHECK (version >= 0),
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    fingerprint CHAR(64) NOT NULL
);
CREATE INDEX ix_request_owner_created ON service_request (owner, created_at DESC, id DESC);
CREATE INDEX ix_request_created ON service_request (created_at DESC, id DESC);

CREATE TABLE request_audit (
    request_id UUID NOT NULL REFERENCES service_request(id),
    version BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (request_id, version)
);
