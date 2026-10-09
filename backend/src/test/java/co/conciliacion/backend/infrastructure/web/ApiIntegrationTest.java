package co.conciliacion.backend.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from payment");
        jdbc.update("delete from invoice");
        jdbc.update("""
            insert into invoice(number, supplier_nit, supplier_name, issue_date, due_date, total, balance)
            values ('FV-1', '900-1', 'Proveedor A', '2026-03-01', '2026-03-15', 100.00, 100.00),
                   ('FV-2', '900-2', 'Proveedor B', '2026-03-01', '2026-03-15', 50.50, 50.50)""");
    }

    private ResultActions postPayment(String reference, String invoice, String amount) throws Exception {
        String body = """
            {"reference":"%s","invoiceNumber":"%s","amount":"%s","paidAt":"2026-03-15T10:00:00-05:00"}"""
            .formatted(reference, invoice, amount);
        return mvc.perform(post("/api/v1/pagos").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void appliedPaymentReturnsBalancesAsStrings() throws Exception {
        postPayment("P-1", "FV-1", "40")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPLIED"))
            .andExpect(jsonPath("$.previousBalance").value("100.00"))
            .andExpect(jsonPath("$.balance").value("60.00"));
    }

    @Test
    void paymentExceedingBalanceReturns422WithReason() throws Exception {
        postPayment("P-1", "FV-1", "101")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reason").value("AMOUNT_EXCEEDS_BALANCE"));
    }

    @Test
    void missingInvoiceReturns422() throws Exception {
        postPayment("P-1", "FV-NOPE", "10")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reason").value("INVOICE_NOT_FOUND"));
    }

    @Test // RF-10
    void decimalAmountIsRejectedAtTheEdge() throws Exception {
        postPayment("P-1", "FV-1", "40.50").andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByNitAndReturnsAmountsAsStrings() throws Exception {
        mvc.perform(get("/api/v1/facturas").param("nit", "900-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].number").value("FV-1"))
            .andExpect(jsonPath("$.items[0].total").value("100.00"))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        postPayment("P-1", "FV-1", "40");
        mvc.perform(get("/api/v1/facturas").param("status", "PARTIAL"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].number").value("FV-1"));
    }
}