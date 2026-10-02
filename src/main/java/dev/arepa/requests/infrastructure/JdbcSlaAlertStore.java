package dev.arepa.requests.infrastructure;

import dev.arepa.requests.application.SlaAlertStore;
import dev.arepa.requests.domain.Priority;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcSlaAlertStore implements SlaAlertStore {
    private final JdbcTemplate jdbc;
    public JdbcSlaAlertStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public int detect(Instant now) {
        return jdbc.update("INSERT INTO sla_alert(request_id,detected_at) SELECT id,? FROM service_request WHERE status IN ('OPEN','IN_PROGRESS') AND sla_due_at < ? ON CONFLICT DO NOTHING", Timestamp.from(now), Timestamp.from(now));
    }
    public List<Alert> active(int limit) {
        return jdbc.query("SELECT r.id,r.title,r.priority,r.sla_due_at,r.assigned_to,a.detected_at FROM sla_alert a JOIN service_request r ON a.request_id = r.id WHERE r.status IN ('OPEN','IN_PROGRESS') ORDER BY r.sla_due_at,r.id LIMIT ?", (row,index) -> new Alert(row.getObject("id",UUID.class),row.getString("title"),Priority.valueOf(row.getString("priority")),row.getTimestamp("sla_due_at").toInstant(),row.getTimestamp("detected_at").toInstant(),row.getString("assigned_to")), limit);
    }
}
