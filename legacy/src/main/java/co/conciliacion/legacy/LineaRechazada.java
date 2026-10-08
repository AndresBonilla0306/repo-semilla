package co.conciliacion.legacy;

/*La linea del archivo que no se puden convertir en un pago con número  */
public record LineaRechazada(int numeroLinea, String contenido, String motivo) {
    
}
