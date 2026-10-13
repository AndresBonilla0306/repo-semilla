package co.conciliacion.backend.infrastructure.web;

import co.conciliacion.backend.application.batch.RawLine;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Parseo del CSV de Tesoreria. Misma logica que el importador corregido de legacy/. */
final class CsvBatchParser {

    private static final int MAX_LINES = 100_000;
    private static final String[] HEADER = {"referencia", "numero_factura", "valor", "fecha_pago"};

    private CsvBatchParser() { }

    static List<RawLine> parse(byte[] bytes) {
        String text = decode(bytes);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }
        String[] lines = text.split("\\R", -1);
        if (!validHeader(lines[0])) {
            throw new IllegalArgumentException(
                "Encabezado invalido. Se esperaba: " + String.join(";", HEADER));
        }
        List<RawLine> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            if (out.size() >= MAX_LINES) {
                throw new IllegalArgumentException(
                    "El archivo supera el limite de " + MAX_LINES + " lineas");
            }
            String[] f = lines[i].split(";", -1);
            if (f.length != HEADER.length) {
                out.add(new RawLine(i + 1, null, null, null, null,
                    "Se esperaban 4 columnas y llegaron " + f.length));
            } else {
                out.add(new RawLine(i + 1, clean(f[0]), clean(f[1]), clean(f[2]), clean(f[3]), null));
            }
        }
        return out;
    }

    private static boolean validHeader(String line) {
        String[] f = line.split(";", -1);
        if (f.length != HEADER.length) return false;
        for (int i = 0; i < HEADER.length; i++) {
            if (!clean(f[i]).equals(HEADER[i])) return false;
        }
        return true;
    }

    /** strip() no quita el espacio no separable (U+00A0) que suele venir de Excel. */
    private static String clean(String s) {
        return s.replace('\u00A0', ' ').strip();
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }
}