package co.conciliacion.backend.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.conciliacion.backend.application.batch.BatchCommand;
import co.conciliacion.backend.application.batch.BatchSummary;
import co.conciliacion.backend.application.batch.RawLine;
import co.conciliacion.backend.application.port.in.BatchUseCase;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BatchIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BatchUseCase batchUseCase;

    @BeforeEach
    void seed() {
        jdbc.update("delete from batch_line");
        jdbc.update("delete from batch");
        jdbc.update("delete from payment_audit");
        jdbc.update("delete from payment");
        jdbc.update("delete from invoice");
        jdbc.update("""
            insert into invoice(number, supplier_nit, supplier_name, issue_date, due_date, total, balance)
            values ('FV-1', '900-1', 'Proveedor A', '2026-03-01', '2026-03-15', 100.00, 100.00)""");
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("select balance from invoice where number = 'FV-1'", BigDecimal.class);
    }

    private static final String JSON_BATCH = """
        {"loteId":"L-1","origen":"TESORERIA","fechaGeneracion":"2026-03-15","pagos":[
          {"referencia":"P-1","numeroFactura":"FV-1","valor":"60","fechaPago":"2026-03-15T10:00:00-05:00"},
          {"referencia":"P-2","numeroFactura":"FV-1","valor":"70","fechaPago":"2026-03-15T10:00:00-05:00"}]}""";

    @Test // CF-03 y CF-06 por HTTP
    void resendingTheSameBatchLeavesBalancesIdentical() throws Exception {
        mvc.perform(post("/api/v1/lotes").contentType(MediaType.APPLICATION_JSON).content(JSON_BATCH))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.aplicadas").value(1))
            .andExpect(jsonPath("$.rechazadas").value(1))
            .andExpect(jsonPath("$.reenvio").value(false));
        BigDecimal afterFirst = balance();

        mvc.perform(post("/api/v1/lotes").contentType(MediaType.APPLICATION_JSON).content(JSON_BATCH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reenvio").value(true))
            .andExpect(jsonPath("$.aplicadas").value(1));

        assertEquals(0, afterFirst.compareTo(balance()));
        assertEquals(0, new BigDecimal("40").compareTo(balance()));
    }

    @Test
    void sameIdWithDifferentContentReturns409() throws Exception {
        mvc.perform(post("/api/v1/lotes").contentType(MediaType.APPLICATION_JSON).content(JSON_BATCH))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/lotes").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_BATCH.replace("\"valor\":\"60\"", "\"valor\":\"61\"")))
            .andExpect(status().isConflict());
    }

    @Test // CSV con una linea invalida, consulta filtrada y descarga
    void csvUploadReportsPerLineAndAllowsFilterAndDownload() throws Exception {
        String csv = "referencia;numero_factura;valor;fecha_pago\n"
            + "P-1;FV-1;60;2026-03-15T10:00:00-05:00\n"
            + "P-2;FV-1;100.5;2026-03-15T10:00:00-05:00\n";
        MockMultipartFile file = new MockMultipartFile("file", "lote.csv", "text/csv",
            csv.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/lotes/archivo").file(file).param("loteId", "CSV-1"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.aplicadas").value(1))
            .andExpect(jsonPath("$.rechazadas").value(1));

        mvc.perform(get("/api/v1/lotes/CSV-1").param("reason", "INVALID_LINE"))
            .andExpect(jsonPath("$.lineas.totalElements").value(1))
            .andExpect(jsonPath("$.lineas.items[0].numeroLinea").value(3));

        mvc.perform(get("/api/v1/lotes/CSV-1/resultado"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("INVALID_LINE")));
    }

    @Test
    void csvWithInvalidHeaderReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lote.csv", "text/csv",
            "a;b;c\n1;2;3\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/lotes/archivo").file(file)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownBatchReturns404() throws Exception {
        mvc.perform(get("/api/v1/lotes/NOPE")).andExpect(status().isNotFound());
    }

    @Test // CF-04: dos lotes distintos y concurrentes sobre la misma factura
    void cf04_twoConcurrentBatchesNeverExceedOriginalBalance() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<BatchSummary> a = () -> {
            start.await();
            return batchUseCase.process(cmd("L-A", "A", "60"));
        };
        Callable<BatchSummary> b = () -> {
            start.await();
            return batchUseCase.process(cmd("L-B", "B", "70"));
        };
        List<Future<BatchSummary>> futures = List.of(pool.submit(a), pool.submit(b));
        start.countDown();
        int applied = 0;
        for (Future<BatchSummary> f : futures) {
            applied += f.get(15, TimeUnit.SECONDS).appliedLines();
        }
        pool.shutdown();

        assertEquals(1, applied);
        assertTrue(balance().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(balance().compareTo(new BigDecimal("30")) == 0
            || balance().compareTo(new BigDecimal("40")) == 0);
    }

    private BatchCommand cmd(String id, String ref, String amount) {
        return new BatchCommand(id, "TESORERIA", LocalDate.of(2026, 3, 15),
            List.of(new RawLine(1, ref, "FV-1", amount, "2026-03-15T10:00:00-05:00", null)));
    }
}