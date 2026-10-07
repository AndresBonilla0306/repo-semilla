package co.conciliacion.legacy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ImportadorCsvLegacyCaracterizacionTest {

    private static final String ENC = "referencia;numero_factura;valor;fecha_pago\n";
    private static final String OK = "P-1;FV-1;1000;2026-03-15T23:30:00-05:00\n";

    @TempDir Path dir;
    private final ImportadorCsvLegacy importador = new ImportadorCsvLegacy();

    private ResultadoImportacion importar(byte[] contenido) throws IOException {
        Path p = dir.resolve("lote.csv");
        Files.write(p, contenido);
        return importador.importar(p);
    }

    private ResultadoImportacion importar(String contenido) throws IOException {
        return importar(contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aceptaArchivoValidoYConservaElOffset() throws IOException {
        ResultadoImportacion r = importar(ENC + OK);
        assertTrue(r.fueAceptado());
        assertEquals(1000L, r.getPagos().get(0).getValor());
        assertEquals(ZoneOffset.ofHours(-5), r.getPagos().get(0).getFechaPago().getOffset());
    }

    @Test
    void rechazaArchivoVacio() throws IOException {
        assertEquals("El archivo esta vacio", importar("").getMotivoRechazo());
    }

    @Test
    void rechazaTodoSiElArchivoTraeBom() throws IOException {
        ResultadoImportacion r = importar("\uFEFF" + ENC + OK);
        assertFalse(r.fueAceptado());
        assertTrue(r.getMotivoRechazo().startsWith("Encabezado invalido"));
    }

    @Test
    void rechazaTodoSiElArchivoNoEsUtf8() throws IOException {
        byte[] latin1 = (ENC + "P-\u00D11;FV-1;1000;2026-03-15T23:30:00-05:00\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        ResultadoImportacion r = importar(latin1);
        assertFalse(r.fueAceptado());
        assertTrue(r.getMotivoRechazo().contains("MalformedInputException"));
    }

    @Test
    void unaLineaConCampoFinalVacioTumbaTodoElLote() throws IOException {
        ResultadoImportacion r = importar(ENC + OK + "P-2;FV-2;500;\n");
        assertFalse(r.fueAceptado());
        assertTrue(r.getMotivoRechazo().contains("ArrayIndexOutOfBoundsException"));
    }

    @Test
    void valorConDecimalesRechazaTodoElLote() throws IOException {
        ResultadoImportacion r = importar(ENC + "P-1;FV-1;100.00;2026-03-15T23:30:00-05:00\n");
        assertFalse(r.fueAceptado());
        assertTrue(r.getMotivoRechazo().contains("NumberFormatException"));
    }

    @Test
    void fechaSinOffsetRechazaTodoElLote() throws IOException {
        ResultadoImportacion r = importar(ENC + "P-1;FV-1;100;2026-03-15T23:30:00\n");
        assertFalse(r.fueAceptado());
        assertTrue(r.getMotivoRechazo().contains("DateTimeParseException"));
    }

    @Test
    void ignoraLineasEnBlancoPeroLasCuentaParaElLimite() throws IOException {
        StringBuilder sb = new StringBuilder(ENC);
        for (int i = 0; i < 100_000; i++) {
            sb.append("P-").append(i).append(";FV-1;1;2026-03-15T10:00:00-05:00\n");
        }
        assertTrue(importar(sb.toString()).fueAceptado());      // 100.000 pasa
        assertFalse(importar(sb + "\n").fueAceptado());         // + 1 línea en blanco: rechazado
    }
}