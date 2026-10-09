1	BOM UTF-8	                    ImportadorCsvLegacy.java (validarEncabezado)
2	No UTF-8	                    ImportadorCsvLegacy.java (Files.lines(..., UTF_8))
3	Límite cuenta líneas en blanco	ImportadorCsvLegacy.java (lineas.size() - 1)
4	split pierde campos vacíos	    ImportadorCsvLegacy.java (linea.split)
5	parseLong estricto	            ImportadorCsvLegacy.java
6	Stream sin cerrar	            ImportadorCsvLegacy.java
7	Todo o nada	ResultadoImportacion.java (el contrato) y el catch del importador

# DECISIONS

## 1. Módulo heredado (legacy/)

### Metodología
1. Pruebas de caracterización contra el código original (8 pruebas), commiteadas antes de modificar.
2. Congelé el código original en `src/test/.../original/` para que esas pruebas sigan corriendo en verde: demuestran qué comportamiento se reemplazó, sin usar `@Disabled`.
3. Reescribí el importador y agregué pruebas del comportamiento nuevo (9 pruebas).

### Defectos encontrados
| # | Defecto | Efecto | Corrección |
|---|---|---|---|
| 1 | BOM UTF-8 | El encabezado no coincidía y se rechazaba todo el archivo | Se quita el BOM antes de validar |
| 2 | Archivo no UTF-8 | `Files.lines` lanzaba una excepción perezosa que se convertía en rechazo total | UTF-8 estricto; si falla, Windows-1252 con advertencia en el resultado |
| 3 | Límite contaba líneas en blanco | Archivos grandes se rechazaban de más | Solo cuentan líneas con contenido |
| 4 | `split` descartaba campos vacíos al final | Una línea mala lanzaba `ArrayIndexOutOfBounds` y tumbaba todo el lote | `split(";", -1)` y validación de 4 columnas por línea |
| 5 | `parseLong` estricto | Un valor con decimales o espacio no separable tumbaba el lote | Validación con regex y rechazo solo de esa línea |
| 6 | Stream sin cerrar | Fuga de descriptores de archivo | Lectura con `readAllBytes` |
| 7 | Todo o nada | Contradice RF-21 | Rechazo por línea con número de línea y motivo |

### Por qué no se manifestaba en desarrollo
Los archivos de desarrollo se generan por script: ASCII puro, sin BOM, con datos completos y poco volumen. Los rechazos en producción provienen de archivos exportados desde Excel o desde equipos con otra configuración regional, que introducen BOM, codificación Windows-1252, campos vacíos al final o valores con separadores, y de volúmenes cercanos al límite. El código nunca vio esas entradas fuera de producción, y los mensajes de error sin número de línea impedían diagnosticarlo. Cartera no respondió con el mensaje exacto, así que esto es una hipótesis razonada, no una confirmación.

### Alternativas descartadas
- **Corregir en el lugar sin cambiar el contrato:** menor riesgo, pero deja el rechazo total y contradice RF-21.
- **Rechazar archivos no UTF-8:** corta el síntoma, pero castiga a Tesorería por un problema de codificación. Riesgo aceptado de Windows-1252: un archivo en otra codificación se leería con caracteres erróneos, por eso queda la advertencia.
- **Aceptar `1.000` o `100.00` adivinando:** en pesos colombianos el punto es separador de miles. Adivinar mal es dinero, así que se rechaza con motivo.

### Supuestos
- Hay rechazo del archivo entero solo si está vacío, el encabezado es inválido, es ilegible o supera 100.000 líneas con contenido.
- Valores solo con dígitos ASCII y mayores que cero.
- El número de línea es físico, con el encabezado como línea 1.