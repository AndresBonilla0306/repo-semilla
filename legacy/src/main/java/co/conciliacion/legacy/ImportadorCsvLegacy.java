package co.conciliacion.legacy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Importador de archivos de pago entregados por Tesoreria.
 *
 * En produccion desde 2022. Formato acordado con Tesoreria:
 *   referencia;numero_factura;valor;fecha_pago
 *
 * Historial de cambios:
 *   2022-04  version inicial
 *   2022-11  se agrega validacion de encabezado tras un incidente de formato
 *   2023-06  se sube el limite de lineas de 10.000 a 100.000
 * 
 * Reescrito: el archivo se rechaza entero solo por defectos globales; cada linea
 * invalida se reporta con su numero y motivo sin tumbar las demas.
 */
public class ImportadorCsvLegacy {

    private static final String SEPARADOR = ";";
    private static final int COLUMNAS = 4;
    private static final int MAX_LINEAS = 100_000;
    private static final char BOM = '\uFEFF';
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final Pattern ENTERO = Pattern.compile("\\d{1,18}");
    
    private static final String[] ENCABEZADO_ESPERADO = {
        "referencia", "numero_factura", "valor", "fecha_pago"
    };

    /**
     * Lee el archivo completo y devuelve las lineas de pago que contiene.
     * Si el archivo no cumple el formato acordado, se rechaza entero.
     */
    public ResultadoImportacion importar(Path archivo) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(archivo);
        } catch (IOException e) {
            return ResultadoImportacion.rechazado("No se pudo leer el archivo: " + e.getMessage());
        }

        List<String> advertencias = new ArrayList<>();
        String texto = decodificar(bytes, advertencias);
        if (!texto.isEmpty() && texto.charAt(0) == BOM) {
            texto = texto.substring(1);
        }
        if (texto.isBlank()) {
            return ResultadoImportacion.rechazado("El archivo esta vacio");
        }

        String[] lineas = texto.split("\\R", -1);
        if (!encabezadoValido(lineas[0])) {
            return ResultadoImportacion.rechazado(
                "Encabezado invalido. Se esperaba: " + String.join(SEPARADOR, ENCABEZADO_ESPERADO));
        }

        int conContenido = 0;
        for (int i = 1; i < lineas.length; i++) {
            if (!lineas[i].isBlank()) {
                conContenido++;
            }
        }
        if (conContenido > MAX_LINEAS) {
            return ResultadoImportacion.rechazado(
                "El archivo supera el limite de " + MAX_LINEAS + " lineas");
        }

        List<LineaPago> pagos = new ArrayList<>(conContenido);
        List<LineaRechazada> rechazadas = new ArrayList<>();
        for (int i = 1; i < lineas.length; i++) {
            if (!lineas[i].isBlank()) {
                procesarLinea(i + 1, lineas[i], pagos, rechazadas);
            }
        }
        return ResultadoImportacion.aceptado(pagos, rechazadas, advertencias);
    }

    /** UTF-8 estricto; si falla, Windows-1252 con advertencia. */
    private String decodificar(byte[] bytes, List<String> advertencias) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            advertencias.add("El archivo no es UTF-8 valido; se leyo como Windows-1252");
            return new String(bytes, WINDOWS_1252);
        }
    }
    
   private boolean encabezadoValido(String linea) {
        String[] campos = linea.split(SEPARADOR, -1);
        if (campos.length != COLUMNAS) {
            return false;
        }
        for (int i = 0; i < COLUMNAS; i++) {
            if (!limpiar(campos[i]).equals(ENCABEZADO_ESPERADO[i])) {
                return false;
            }
        }
        return true;
    }

    private void procesarLinea(int numero, String linea, List<LineaPago> pagos,
                               List<LineaRechazada> rechazadas) {
        String[] campos = linea.split(SEPARADOR, -1);
        if (campos.length != COLUMNAS) {
            rechazadas.add(new LineaRechazada(numero, linea,
                "Se esperaban " + COLUMNAS + " columnas y llegaron " + campos.length));
            return;
        }
        String referencia = limpiar(campos[0]);
        String numeroFactura = limpiar(campos[1]);
        String valorTexto = limpiar(campos[2]);
        String fechaTexto = limpiar(campos[3]);

        if (referencia.isEmpty()) {
            rechazadas.add(new LineaRechazada(numero, linea, "La referencia esta vacia"));
            return;
        }
        if (numeroFactura.isEmpty()) {
            rechazadas.add(new LineaRechazada(numero, linea, "El numero de factura esta vacio"));
            return;
        }
        if (!ENTERO.matcher(valorTexto).matches()) {
            rechazadas.add(new LineaRechazada(numero, linea,
                "El valor debe ser un entero sin separadores ni decimales: '" + valorTexto + "'"));
            return;
        }
        long valor = Long.parseLong(valorTexto);
        if (valor <= 0) {
            rechazadas.add(new LineaRechazada(numero, linea, "El valor debe ser mayor que cero"));
            return;
        }
        OffsetDateTime fecha;
        try {
            fecha = OffsetDateTime.parse(fechaTexto);
        } catch (DateTimeParseException e) {
            rechazadas.add(new LineaRechazada(numero, linea,
                "Fecha invalida o sin offset: '" + fechaTexto + "'"));
            return;
        }
        pagos.add(new LineaPago(referencia, numeroFactura, valor, fecha));
    }

    /** trim() no quita el espacio no separable (U+00A0) que suele venir de Excel. */
    private String limpiar(String texto) {
        return texto.replace('\u00A0', ' ').strip();
    }
}
