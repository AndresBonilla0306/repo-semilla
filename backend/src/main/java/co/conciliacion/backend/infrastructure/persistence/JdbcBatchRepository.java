package co.conciliacion.backend.infrastructure.persistence;

import co.conciliacion.backend.application.batch.BatchSummary;
import co.conciliacion.backend.application.batch.LineResult;
import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.application.port.out.BatchRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcBatchRepository implements BatchRepository {

    private static final String LINE_COLUMNS =
        "line_number, reference, invoice_number, amount, outcome, reason, detail";

    private final JdbcTemplate jdbc;

    public JdbcBatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<LineResult> LINE_MAPPER = (rs, i) -> new LineResult(
        rs.getInt("line_number"), rs.getString("reference"), rs.getString("invoice_number"),
        rs.getString("amount"), rs.getString("outcome"), rs.getString("reason"), rs.getString("detail"));

    @Override
    public Optional<BatchSummary> find(String batchId) {
        return jdbc.query("""
            select id, origin, generated_at, content_hash, status,
                   total_lines, applied_lines, rejected_lines
            from batch where id = ?""",
            (rs, i) -> new BatchSummary(rs.getString("id"), rs.getString("origin"),
                rs.getObject("generated_at", LocalDate.class), rs.getString("content_hash"),
                rs.getString("status"), rs.getInt("total_lines"), rs.getInt("applied_lines"),
                rs.getInt("rejected_lines"), false),
            batchId).stream().findFirst();
    }

    @Override
    public boolean begin(String batchId, String origin, LocalDate generatedAt,
                         String contentHash, Instant now) {
        return jdbc.update("""
            insert into batch(id, origin, generated_at, content_hash, status, created_at)
            values (?, ?, ?, ?, 'PROCESSING', ?)
            on conflict (id) do nothing""",
            batchId, origin, generatedAt, contentHash,
            OffsetDateTime.ofInstant(now, ZoneOffset.UTC)) == 1;
    }

    @Override
    @Transactional
    public void finish(String batchId, List<LineResult> lines, int applied, int rejected) {
        jdbc.batchUpdate("""
            insert into batch_line(batch_id, line_number, reference, invoice_number, amount,
                                   outcome, reason, detail)
            values (?, ?, ?, ?, ?, ?, ?, ?)""", lines, 1000, (ps, l) -> {
                ps.setString(1, batchId);
                ps.setInt(2, l.lineNumber());
                ps.setString(3, l.reference());
                ps.setString(4, l.invoiceNumber());
                ps.setString(5, l.amount());
                ps.setString(6, l.outcome());
                ps.setString(7, l.reason());
                ps.setString(8, l.detail());
            });
        jdbc.update("""
            update batch set status = 'DONE', total_lines = ?, applied_lines = ?, rejected_lines = ?
            where id = ?""", lines.size(), applied, rejected, batchId);
    }

    @Override
    public PageResult<LineResult> lines(String batchId, String reason, int page, int size) {
        String where = reason == null ? "" : " and reason = ?";
        List<Object> countArgs = new ArrayList<>(List.of(batchId));
        if (reason != null) countArgs.add(reason);
        Long total = jdbc.queryForObject(
            "select count(*) from batch_line where batch_id = ?" + where, Long.class, countArgs.toArray());

        List<Object> args = new ArrayList<>(countArgs);
        args.add(size);
        args.add((long) page * size);
        List<LineResult> items = jdbc.query("select " + LINE_COLUMNS
            + " from batch_line where batch_id = ?" + where
            + " order by line_number limit ? offset ?", LINE_MAPPER, args.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public List<LineResult> allLines(String batchId) {
        return jdbc.query("select " + LINE_COLUMNS
            + " from batch_line where batch_id = ? order by line_number", LINE_MAPPER, batchId);
    }
}