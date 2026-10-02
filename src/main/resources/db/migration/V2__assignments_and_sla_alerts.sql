ALTER TABLE service_request ALTER COLUMN owner TYPE VARCHAR(200);
ALTER TABLE service_request ALTER COLUMN idempotency_key TYPE VARCHAR(350);
ALTER TABLE request_audit ALTER COLUMN actor TYPE VARCHAR(200);
ALTER TABLE request_audit ADD COLUMN detail VARCHAR(512);
CREATE TABLE operator_profile (
    subject VARCHAR(200) PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL
);
ALTER TABLE service_request ADD COLUMN assigned_to VARCHAR(200) REFERENCES operator_profile(subject);
CREATE TABLE sla_alert (
    request_id UUID PRIMARY KEY REFERENCES service_request(id),
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_request_open_deadline ON service_request(status,sla_due_at);
