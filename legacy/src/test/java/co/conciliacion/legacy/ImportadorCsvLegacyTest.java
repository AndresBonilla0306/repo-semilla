package co.conciliacion.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImportadorCsvLegacyTest {

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
    void aceptaBomEnElEncabezado() throws IOException {
        ResultadoImportacion r = importar("\uFEFF" + ENC + OK);
        assertTrue(r.fueAceptado());
        assertEquals(1, r.getPagos().size());
    }

    @Test
    void leeWindows1252ConAdvertencia() throws IOException {
        byte[] latin = (ENC + "P-\u00D11;FV-1;1000;2026-03-15T23:30:00-05:00\n")
            .getBytes(java.nio.charset.Charset.forName("windows-1252"));
        ResultadoImportacion r = importar(latin);
        assertTrue(r.fueAceptado());
        assertEquals("P-\u00D11", r.getPagos().get(0).getReferencia());
        assertEquals(1, r.getAdvertencias().size());
    }

    @Test
    void campoFinalVacioRechazaSoloEsaLinea() throws IOException {
        ResultadoImportacion r = importar(ENC + OK + "P-2;FV-2;500;\n");
        assertTrue(r.fueAceptado());
        assertEquals(1, r.getPagos().size());
        assertEquals(1, r.getLineasRechazadas().size());
        assertEquals(3, r.getLineasRechazadas().get(0).numeroLinea());
    }

    @Test
    void valorConDecimalesRechazaSoloEsaLinea() throws IOException {
        ResultadoImportacion r = importar(ENC + OK + "P-2;FV-2;100.00;2026-03-15T10:00:00-05:00\n");
        assertEquals(1, r.getPagos().size());
        assertTrue(r.getLineasRechazadas().get(0).motivo().contains("entero"));
    }

    @Test
    void espacioNoSeparableEnElValorSeLimpia() throws IOException {
        ResultadoImportacion r = importar(ENC + "P-1;FV-1;\u00A01000;2026-03-15T10:00:00-05:00\n");
        assertEquals(1000L, r.getPagos().get(0).getValor());
    }

    @Test
    void fechaSinOffsetRechazaSoloEsaLinea() throws IOException {
        ResultadoImportacion r = importar(ENC + OK + "P-2;FV-2;100;2026-03-15T10:00:00\n");
        assertEquals(1, r.getPagos().size());
        assertEquals(1, r.getLineasRechazadas().size());
    }

    @Test
    void lineasEnBlancoNoCuentanParaElLimite() throws IOException {
        StringBuilder sb = new StringBuilder(ENC);
        for (int i = 0; i < 100_000; i++) {
            sb.append("P-").append(i).append(";FV-1;1;2026-03-15T10:00:00-05:00\n");
        }
        sb.append("\n\n\n");
        assertTrue(importar(sb.toString()).fueAceptado());
    }

    @Test
    void masDelLimiteRechazaElArchivo() throws IOException {
        StringBuilder sb = new StringBuilder(ENC);
        for (int i = 0; i < 100_001; i++) {
            sb.append("P-").append(i).append(";FV-1;1;2026-03-15T10:00:00-05:00\n");
        }
        assertFalse(importar(sb.toString()).fueAceptado());
    }

    @Test
    void archivoVacioSeRechaza() throws IOException {
        assertEquals("El archivo esta vacio", importar("").getMotivoRechazo());
    }
}