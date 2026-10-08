package co.conciliacion.legacy;
import java.util.Collections;
import java.util.List;

/** Resultado de leer un archivo de pagos: o se acepta entero, o se rechaza entero. 
 * El archivo se rechaza entero solo por problemas de formato global (vacio, encabezado,
 * ilegible, exceso de lineas). Los problemas de una linea se reportan por linea.
 * 
*/
public class ResultadoImportacion {

    private final boolean aceptado;
    private final String motivoRechazo;
    private final List<LineaPago> pagos;
    private final List<LineaRechazada> lineasRechazadas;
    private final List<String> advertencias;

    private ResultadoImportacion(boolean aceptado, String motivoRechazo, List<LineaPago> pagos, 
                                    List<LineaRechazada> lineasRechazadas, List<String> advertencias) {
        this.aceptado = aceptado;
        this.motivoRechazo = motivoRechazo;
        this.pagos = Collections.unmodifiableList(pagos);
        this.lineasRechazadas = Collections.unmodifiableList(lineasRechazadas);
        this.advertencias = Collections.unmodifiableList(advertencias);
    }

    public static ResultadoImportacion aceptado(List<LineaPago> pagos,
                                                 List<LineaRechazada> rechazadas, 
                                                List<String> advertencias
    ) {
        return new ResultadoImportacion(true, null, pagos, rechazadas, advertencias);
    }

    public static ResultadoImportacion rechazado(String motivo) {
        return new ResultadoImportacion(false, motivo, List.of(), List.of(), List.of());
    }

    public boolean fueAceptado() {
        return aceptado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public List<LineaPago> getPagos() {
        return pagos;
    }

    public List<LineaRechazada> getLineasRechazadas() { 
        return lineasRechazadas;
    }

    public List<String> getAdvertencias(){
        return advertencias;
    }
}
