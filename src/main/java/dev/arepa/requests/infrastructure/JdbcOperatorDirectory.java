package dev.arepa.requests.infrastructure;

import dev.arepa.requests.application.OperatorDirectory;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcOperatorDirectory implements OperatorDirectory {
    private final JdbcTemplate jdbc;
    public JdbcOperatorDirectory(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void register(String subject, String displayName) {
        jdbc.update("INSERT INTO operator_profile(subject,display_name) VALUES (?,?) ON CONFLICT DO NOTHING", subject, displayName);
    }
    public boolean exists(String subject) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM operator_profile WHERE subject = ?)", Boolean.class, subject));
    }
    public List<Operator> list() {
        return jdbc.query("SELECT subject,display_name FROM operator_profile ORDER BY display_name,subject LIMIT 100", (row,index) -> new Operator(row.getString("subject"),row.getString("display_name")));
    }
}
