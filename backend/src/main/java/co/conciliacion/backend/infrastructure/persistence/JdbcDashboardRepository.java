package co.conciliacion.backend.infrastructure.persistence;

import co.conciliacion.backend.application.dashboard.Dashboard;
import co.conciliacion.backend.application.port.out.DashboardQueryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDashboardRepository implements DashboardQueryPort {

    private final JdbcTemplate jdbc;

    public JdbcDashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Dashboard query(LocalDate dueFrom, LocalDate dueTo) {
        StringBuilder where = new StringBuilder("1=1");
        List<Object> args = new ArrayList<>();
        if (dueFrom != null) { where.append(" and i.due_date >= ?"); args.add(dueFrom); }
        if (dueTo != null) { where.append(" and i.due_date <= ?"); args.add(dueTo); }
        Object[] a = args.toArray();

        BigDecimal invoiced = jdbc.queryForObject(
            "select coalesce(sum(i.total), 0) from invoice i where " + where, BigDecimal.class, a);
        BigDecimal applied = jdbc.queryForObject(
            "select coalesce(sum(p.amount), 0) from payment p join invoice i on i.number = p.invoice_number where "
                + where, BigDecimal.class, a);
        BigDecimal rejected = jdbc.queryForObject(
            "select coalesce(sum(au.amount), 0) from payment_audit au join invoice i on i.number = au.invoice_number "
                + "where au.outcome = 'REJECTED' and " + where, BigDecimal.class, a);
        List<Dashboard.SupplierBalance> top = jdbc.query(
            "select i.supplier_nit, max(i.supplier_name) as name, sum(i.balance) as pending from invoice i where "
                + where + " group by i.supplier_nit order by pending desc, i.supplier_nit limit 10",
            (rs, n) -> new Dashboard.SupplierBalance(rs.getString("supplier_nit"), rs.getString("name"),
                rs.getBigDecimal("pending")), a);
        return new Dashboard(invoiced, applied, rejected, top);
    }
}