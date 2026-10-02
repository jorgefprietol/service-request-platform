package dev.arepa.requests.infrastructure;

import dev.arepa.requests.application.*;
import dev.arepa.requests.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcRequestStore implements RequestStore {
    private final JdbcTemplate jdbc;
    public JdbcRequestStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<ServiceRequest> find(UUID id) {
        return jdbc.query("SELECT * FROM service_request WHERE id = ?", this::map, id).stream().findFirst();
    }

    public Optional<StoredRequest> findByKey(String key) {
        return jdbc.query("SELECT * FROM service_request WHERE idempotency_key = ?",
                (row, index) -> new StoredRequest(map(row, index), row.getString("fingerprint")), key).stream().findFirst();
    }

    public List<ServiceRequest> list(String owner, int limit, int offset) {
        if (owner == null) return jdbc.query("SELECT * FROM service_request ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", this::map, limit, offset);
        return jdbc.query("SELECT * FROM service_request WHERE owner = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", this::map, owner, limit, offset);
    }

    public void insert(ServiceRequest r, String key, String fingerprint) {
        try {
            jdbc.update("INSERT INTO service_request (id,title,description,priority,status,owner,created_at,sla_due_at,version,idempotency_key,fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    r.id(), r.title(), r.description(), r.priority().name(), r.status().name(), r.owner(), Timestamp.from(r.createdAt()), Timestamp.from(r.slaDueAt()), r.version(), key, fingerprint);
        } catch (DuplicateKeyException duplicate) { throw new DuplicateRequestKey(duplicate); }
    }

    public boolean update(ServiceRequest r, long expectedVersion) {
        return jdbc.update("UPDATE service_request SET status = ?, version = ? WHERE id = ? AND version = ?", r.status().name(), r.version(), r.id(), expectedVersion) == 1;
    }

    public void appendAudit(UUID id, AuditEntry entry) {
        jdbc.update("INSERT INTO request_audit (request_id,version,action,actor,occurred_at) VALUES (?,?,?,?,?)", id, entry.version(), entry.action(), entry.actor(), Timestamp.from(entry.occurredAt()));
    }

    public List<AuditEntry> history(UUID id) {
        return jdbc.query("SELECT * FROM request_audit WHERE request_id = ? ORDER BY version", (row, index) -> new AuditEntry(row.getLong("version"), row.getString("action"), row.getString("actor"), row.getTimestamp("occurred_at").toInstant()), id);
    }

    private ServiceRequest map(ResultSet row, int index) throws SQLException {
        return new ServiceRequest(row.getObject("id", UUID.class), row.getString("title"), row.getString("description"), Priority.valueOf(row.getString("priority")), Status.valueOf(row.getString("status")), row.getString("owner"), row.getTimestamp("created_at").toInstant(), row.getTimestamp("sla_due_at").toInstant(), row.getLong("version"));
    }
}
