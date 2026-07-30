package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.api.ThreadPlanOutboxController;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
import java.sql.ResultSet; import java.time.Instant; import java.util.List; import java.util.Map;

/** Polls the transactional backend outbox; no in-memory Spring event is used as a delivery guarantee. */
@Slf4j @Service @RequiredArgsConstructor
public class BackendOutboxConsumer {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final ThreadPlanOutboxController adapter;
    public void consume() {
        List<Row> rows = jdbc.query("select id,event_type,payload,occurred_at from ai_user_outbox where status='PENDING' and available_at <= now(3) order by occurred_at limit 50", (ResultSet rs, int n) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()));
        for (Row row : rows) {
            try {
                @SuppressWarnings("unchecked") Map<String,Object> payload = json.readValue(row.payload, Map.class);
                ThreadPlanOutboxController.Event e = json.convertValue(payload, ThreadPlanOutboxController.Event.class);
                e.setEventId(row.id); e.setType(row.type); e.setOccurredAt(row.occurredAt);
                adapter.accept(e);
                jdbc.update("update ai_user_outbox set status='PUBLISHED', published_at=now(3), lease_owner=null, lease_until=null where id=? and status='PENDING'", row.id);
            } catch (Exception ex) {
                log.warn("Outbox event {} failed: {}", row.id, ex.getMessage());
                jdbc.update("update ai_user_outbox set status='FAILED', last_error_code='ORCHESTRATOR_CONSUME_FAILED' where id=? and status='PENDING'", row.id);
            }
        }
    }
    private record Row(String id, String type, String payload, Instant occurredAt) { }
}
