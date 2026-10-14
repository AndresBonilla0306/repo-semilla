package co.conciliacion.backend.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.domain.model.Money;
import co.conciliacion.backend.domain.model.Payment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class QueryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplyPaymentUseCase applyPayment;

    @BeforeEach
    void seed() {
        jdbc.update("delete from batch_line");
        jdbc.update("delete from batch");
        jdbc.update("delete from payment_audit");
        jdbc.update("delete from payment");
        jdbc.update("delete from invoice");
        jdbc.update("""
            insert into invoice(number, supplier_nit, supplier_name, issue_date, due_date, total, balance) values
            ('FV-1', '900-1', 'Proveedor A', '2026-03-01', '2026-03-15', 100.00, 100.00),
            ('FV-2', '900-2', 'Proveedor B', '2026-03-01', '2026-04-10',  50.50,  50.50),
            ('FV-3', '900-1', 'Proveedor A', '2026-03-01', '2026-06-01', 200.00, 200.00)""");
    }

    private void pay(String ref, String invoice, String amount) {
        applyPayment.apply(new Payment(ref, invoice, Money.of(amount),
            OffsetDateTime.parse("2026-03-10T10:00:00-05:00")));
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        mvc.perform(get("/api/v1/facturas").param("nit", "900-1").param("dueFrom", "2026-05-01"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].number").value("FV-3"));
        mvc.perform(get("/api/v1/facturas").param("balanceMin", "100").param("balanceMax", "200"))
            .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/facturas").param("dueFrom", "2026-04-01").param("dueTo", "2026-04-30"))
            .andExpect(jsonPath("$.items[0].number").value("FV-2"));
    }

    @Test
    void invalidFilterParamsReturn400() throws Exception {
        mvc.perform(get("/api/v1/facturas").param("dueFrom", "no-es-fecha")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/facturas").param("balanceMin", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/facturas").param("dueFrom", "2026-05-01").param("dueTo", "2026-04-01"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void dashboardTotalsAndTopSuppliers() throws Exception {
        pay("P-1", "FV-1", "40");
        pay("P-2", "FV-1", "500"); // rechazado: excede el saldo
        mvc.perform(get("/api/v1/conciliacion/tablero").param("dueFrom", "2026-03-01").param("dueTo", "2026-04-30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalInvoiced").value("150.50"))
            .andExpect(jsonPath("$.totalApplied").value("40.00"))
            .andExpect(jsonPath("$.totalRejected").value("500.00"))
            .andExpect(jsonPath("$.topSuppliers[0].nit").value("900-1"))
            .andExpect(jsonPath("$.topSuppliers[0].pendingBalance").value("60.00"))
            .andExpect(jsonPath("$.topSuppliers[1].nit").value("900-2"));
    }

    @Test 
    void paginationStaysStableWhilePaymentsAreApplied() throws Exception {
        jdbc.update("""
            insert into invoice(number, supplier_nit, supplier_name, issue_date, due_date, total, balance)
            select 'FX-' || lpad(g::text, 4, '0'), '900-9', 'Proveedor X', date '2026-03-01',
                   date '2026-03-15', 100.00, 100.00 from generate_series(1, 120) g""");
        ObjectMapper om = new ObjectMapper();
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (int page = 0; page < 3; page++) {
            String body = mvc.perform(get("/api/v1/facturas").param("nit", "900-9")
                    .param("page", String.valueOf(page)).param("size", "50"))
                .andReturn().getResponse().getContentAsString();
            for (JsonNode item : om.readTree(body).get("items")) {
                seen.add(item.get("number").asText());
                total++;
            }
            pay("PG-" + page + "-a", "FX-0001", "1");   // ya vista
            pay("PG-" + page + "-b", "FX-0120", "1");   // aun no vista
        }
        assertEquals(120, total);
        assertEquals(120, seen.size());
    }
}