# DECISIONS

Cada decisión indica la alternativa descartada y por qué. Cuando el enunciado era ambiguo o se contradecía, se registra el supuesto adoptado y si quedó implementado.

> Cartera no respondió las preguntas del enunciado, así que todos los supuestos son decisiones propias, no confirmaciones del negocio.

## 1. Ambigüedades del enunciado

| # | Ambigüedad | Supuesto adoptado | Estado |
|---|---|---|---|
| A1 | RF-04 (facturas con 2 decimales) vs RF-10 (pagos enteros) | Ver A1 abajo | Parcial |
| A2 | RF-18 (aplicar en orden del lote) vs RF-19 (resultado independiente del orden) | Ver A2 abajo | Supuesto, no implementado |
| A3 | RF-12 (referencia no se aplica dos veces) vs RF-20 (reenvío normal) | Ver A3 abajo | Supuesto, no implementado |
| A4 | RF-13 (vencimiento en hora de Colombia) vs contenedor en UTC | Ver A4 abajo | Supuesto, no implementado |
| A5 | `PAGADA_EXTEMPORANEA` no aparece en la lista de estados de RF-03 | Ver A5 abajo | Supuesto, no implementado |
| A6 | RF-14: estado "calculado", ¿derivado o persistido? | Ver A6 abajo | Implementado (parcial) |
| A7 | RF-15 "usuario" sin autenticación; RF-24 "total rechazado" | Ver A7 abajo | Supuesto, no implementado |
| A8 | RF-12: ¿una referencia rechazada queda "usada"? | Ver A8 abajo | Implementado |

### A1. Residuo de centavos (RF-04 vs RF-10, CF-01 y CF-02)

- **Supuesto:** se aplica la lectura literal. Un pago de 37.500.483 sobre un saldo de 37.500.483,40 se aplica y deja 0,40. Un pago de 37.500.484 excede el saldo y se rechaza completo (RF-09).
- **Problema que queda:** la factura queda `PARTIAL` con un saldo que Tesorería nunca podrá pagar, porque solo opera en pesos enteros.
- **Propuesta no implementada:** considerar `PAID` cuando el saldo sea menor que 1 peso, con un umbral explícito y documentado.
- **Alternativa descartada:** redondear el pago o el saldo. Es dinero inventado o perdido sin que nadie lo decida.

### A2. Orden de las líneas (RF-18 vs RF-19, CF-06)

- **Supuesto:** el orden de aplicación es el del lote (RF-18). Con saldo 100, el pago A de 60 se aplica y el B de 70 se rechaza. RF-19 se lee como determinismo: el mismo lote produce siempre el mismo resultado, sin importar cómo se procese (hilos, reintentos).
- **Alternativa descartada:** ordenar las líneas por un criterio fijo (por ejemplo, la referencia) para que el resultado no dependa del orden de llegada. Cumple RF-19 literalmente, pero contradice RF-18.
- **Costo:** el resultado depende de cómo Tesorería ordena el archivo.

### A3. Reenvío de lotes (RF-12 vs RF-20, CF-03)

- **Supuesto:** reenviar el mismo lote no es un error. Una línea cuya referencia ya se aplicó dentro de ese mismo lote devuelve el resultado original sin tocar saldos. La misma referencia en un lote distinto sí se rechaza como `DUPLICATE_REFERENCE`.
- **Qué exige:** guardar el resultado de cada lote por identificador (RF-22).
- **Alternativa descartada:** rechazar todo reenvío como duplicado. Cumple RF-12, pero convierte una operación normal del negocio en una cascada de rechazos.

### A4. Zona horaria (RF-13 vs UTC, CF-05)

- **Supuesto:** el vencimiento se evalúa en `America/Bogota` con una zona explícita, nunca con la zona por defecto de la JVM. Un pago con fecha `2026-03-15T23:30:00-05:00` sobre una factura que vence el 2026-03-15 llega a tiempo.
- **Por qué importa:** en UTC esa misma fecha es `2026-03-16T04:30Z` y la factura saldría como extemporánea.
- **"Aplicado después de ese instante":** se usa la `fechaPago` de la línea, no el momento en que el servidor procesa. Es la única lectura con la que CF-05 tiene sentido.
- **Estado:** hoy el sistema acepta cualquier fecha sin marcar nada. Se documenta como limitación.

### A5. Estado `PAGADA_EXTEMPORANEA` (RF-03 vs RF-13)

- **Supuesto:** es un cuarto estado, además de `PENDING`, `PARTIAL` y `PAID`. Una factura que se salda con un pago posterior al vencimiento queda en ese estado.
- **No es derivable solo del saldo:** depende de la fecha del pago que llevó el saldo a cero.

### A6. Estado derivado o persistido (RF-14)

- **Hoy:** el estado se deriva del saldo y el total (`Invoice.status()`), y el filtro por estado compara esas columnas.
- **Costo:** el filtro por estado hace barrido completo con 500.000 filas. Con `PAGADA_EXTEMPORANEA` habría que persistir una columna de estado.
- **Alternativa para PR-01:** columna `status` desnormalizada con índice, actualizada en la misma transacción que el saldo.

### A7. Usuario fijo y "total rechazado" (RF-15, RF-24)

- **Supuesto:** el usuario de la auditoría es un valor fijo, porque el enunciado excluye la autenticación.
- **"Total rechazado":** la suma de los valores de las líneas rechazadas. Exige persistir los rechazos, que hoy no se guardan.

### A8. Referencia de un pago rechazado (RF-12)

- **Decisión:** un pago rechazado no consume su referencia. Tesorería puede reenviarlo corregido con la misma referencia.
- **Por qué:** RF-12 habla de "aplicarse" dos veces, y un pago rechazado no se aplicó.
- **Riesgo:** conviene confirmar la lectura con el negocio.

## 2. Decisiones técnicas

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| `BigDecimal` con `RoundingMode.UNNECESSARY` en `Money` | `double`, o redondeo automático | Un valor con más de 2 decimales falla en vez de redondearse. Redondear en silencio es dinero perdido o inventado |
| `NUMERIC(19,2)` en PostgreSQL | `double precision` | Sin pérdida de precisión en la base (RF-05) |
| `Invoice` inmutable: `applyPayment` devuelve una factura nueva | Entidad mutable | Es más corta, pero facilita errores de concurrencia (CF-04) |
| `Payment` con `Money`, no `long` | `long` para respetar RF-10 | RF-10 es una regla de entrada. Se valida en el borde (API y CSV) y el dominio mantiene una sola representación del dinero |
| Rechazo como valor (`PaymentResult` es un `sealed interface`) | Excepciones por rechazo | RF-21 pide reportar cada línea, y un lote de 50.000 líneas pagaría el costo de crear excepciones |
| Arquitectura hexagonal con puertos de entrada y salida | Capas clásicas con servicios `@Service` | El dominio y los casos de uso no importan `org.springframework`, así que se prueban sin base de datos |
| Decorador transaccional en `infrastructure/transaction/` | `@Transactional` en el servicio | Esto último es una línea y funciona, pero mete Spring en `application/`. El costo del decorador es una clase más |
| Bloqueo pesimista (`SELECT ... FOR UPDATE`) | Bloqueo optimista con `@Version` y reintentos | Los lotes concurrentes sobre una misma factura son un caso esperado (CF-04), y reintentar complica el código. Se acepta la espera. La columna `version` queda como segunda defensa |
| Bloquear la factura antes de revisar la referencia | Confiar solo en la clave primaria y capturar la excepción | Con el bloqueo, la segunda petición espera, ve el pago de la primera y recibe un rechazo explicable. Con la excepción, la transacción queda en rollback y entrega un error |
| Tres defensas para RF-12: chequeo en el servicio, bloqueo de la factura, clave primaria de `payment.reference` | Una sola | Si falla una capa, la siguiente evita el doble pago |
| Montos como **string** en el JSON | Números JSON | JavaScript los lee como `double` y pierde precisión (RF-05) |
| RF-10 validado en el borde: `amount` solo acepta dígitos y responde 400 con decimales | Validarlo en el dominio | El dominio sigue con una sola representación del dinero |
| Rechazos con **422** y `reason` y `detail` en el cuerpo. Solicitud mal formada: **400** | 404 y 409 según el motivo | Da más semántica HTTP, pero dificulta reutilizar el mismo formato por línea cuando lleguen los lotes (RF-21) |
| Paginación con `OFFSET` ordenada por `number` | Paginación por cursor | `number` es único e inmutable, así que aplicar pagos no mueve filas entre páginas (PR-03, parcial). El cursor es más estable y rápido en páginas profundas, pero cambia más cosas en el frontend |
| Estados en inglés (`PENDING`, `PARTIAL`, `PAID`) | Estados en español | El enunciado pide identificadores en inglés |
| Esquema gestionado por Flyway y `ddl-auto: validate` | Que Hibernate cree las tablas | El esquema es versionado y reproducible. Hibernate solo lo valida |
| Carga de datos con `COPY` y tabla temporal | Insertar 500.000 filas con JPA | JPA tardaría minutos. `COPY` es el mecanismo nativo de PostgreSQL para carga masiva |
| Índices diferidos: solo `ix_invoice_nit` | Crear todos los índices desde `V1` | Insertar 500.000 filas con índices es más lento. Los índices para los demás filtros quedan pendientes (ver `LIMITACIONES.md`) |
| Testcontainers fijado por propiedad en el `pom.xml` (versión **X.Y.Z**, confirma la que usas) | Saltar a Testcontainers 2.x | La versión que gestiona Spring Boot 3.5 no se entendía con la API del Docker Desktop actual (respuesta 400 con campos vacíos). La 2.x renombra artefactos y cambiaría más cosas de las necesarias |

## 3. Módulo heredado (`legacy/`)

### Metodología

1. Pruebas de caracterización contra el código original (8 pruebas), commiteadas antes de modificar nada.
2. El código original quedó congelado en `src/test/.../original/` para que esas pruebas sigan en verde. Demuestran qué comportamiento se reemplazó, sin usar `@Disabled`.
3. Reescritura del importador con 9 pruebas del comportamiento nuevo.

### Defectos encontrados

| # | Defecto | Efecto | Corrección |
|---|---|---|---|
| 1 | BOM UTF-8 | El encabezado no coincidía y se rechazaba todo el archivo | Se quita el BOM antes de validar |
| 2 | Archivo no UTF-8 | `Files.lines` lanzaba una excepción perezosa que se convertía en rechazo total | UTF-8 estricto. Si falla, Windows-1252 con advertencia en el resultado |
| 3 | El límite contaba líneas en blanco | Archivos grandes se rechazaban de más | Solo cuentan las líneas con contenido |
| 4 | `split` descartaba campos vacíos al final | Una línea mala lanzaba `ArrayIndexOutOfBounds` y tumbaba todo el lote | `split(";", -1)` y validación de 4 columnas por línea |
| 5 | `parseLong` estricto | Un valor con decimales o espacio no separable tumbaba el lote | Validación con expresión regular y rechazo solo de esa línea |
| 6 | Stream sin cerrar | Fuga de descriptores de archivo | Lectura con `readAllBytes` |
| 7 | Todo o nada | Contradice RF-21 | Rechazo por línea con número de línea y motivo |

### Por qué no se manifestaba en desarrollo

Los archivos de desarrollo se generan por script: ASCII puro, sin BOM, con datos completos y poco volumen. Los rechazos en producción provienen de archivos exportados desde Excel o desde equipos con otra configuración regional, que introducen BOM, codificación Windows-1252, campos vacíos al final o valores con separadores, y de volúmenes cercanos al límite. El código nunca vio esas entradas fuera de producción, y los mensajes de error sin número de línea impedían diagnosticarlo.

Es una hipótesis razonada, no una confirmación, porque Cartera no entregó el mensaje de rechazo real. Mi sospecha inicial era la zona horaria, pero la descarté al leer el código: `OffsetDateTime.parse` exige el offset explícito y no usa la zona del contenedor.

### Decisiones del módulo

- **Reescribir en lugar de corregir en el lugar:** corregir solo los defectos 1 a 6 era menos riesgoso, pero dejaba el rechazo total y contradecía RF-21.
- **Rechazar solo el archivo vacío, con encabezado inválido, ilegible o con más de 100.000 líneas con contenido.** Todo lo demás se rechaza por línea.
- **Archivos no UTF-8:** se leen como Windows-1252 con advertencia. Rechazarlos cortaba el síntoma, pero castigaba a Tesorería por un problema de codificación. El riesgo es que un archivo en otra codificación se lea con caracteres erróneos.
- **Valores como `1.000` o `100.00`:** se rechazan con motivo. En pesos colombianos el punto es separador de miles, así que adivinar mal es dinero.
- **Número de línea:** físico, con el encabezado como línea 1.

## 4. Casos frontera (Anexo B)

| Caso | Resultado considerado correcto | Estado |
|---|---|---|
| CF-01 | El pago se aplica y la factura queda `PARTIAL` con 0,40 de saldo (ver A1) | Probado |
| CF-02 | Se rechaza completo con `AMOUNT_EXCEEDS_BALANCE` y el saldo no cambia | Probado |
| CF-03 | El saldo tras el segundo envío es idéntico al del primero (ver A3) | A nivel de pago único: `DUPLICATE_REFERENCE`. Lotes: sin implementar |
| CF-04 | La suma aplicada nunca supera el saldo original. Un pago se aplica y el otro se rechaza | Probado con dos hilos contra PostgreSQL real |
| CF-05 | La factura no queda extemporánea (ver A4) | Sin implementar |
| CF-06 | A se aplica y B se rechaza (ver A2) | Sin implementar a nivel de lote |
| CF-07 | Carga de 50.000 líneas dentro de 60 segundos | Sin implementar ni medir |
| CF-08 | La paginación no repite ni omite facturas | Diseñada con orden por `number`. No medida |


PAID_LATE es el cuarto estado. Ya no se puede derivar solo del saldo, porque depende de la fecha del pago que lo saldó. Por eso el estado se persiste en una columna con índice, lo que además ayuda a PR-01.
El vencimiento se evalúa con la paidAt del pago, no con la hora del servidor, y en zona America/Bogota fija. La JVM en UTC no influye.
Un pago es tardío si cae a partir de las 00:00 del día siguiente al vencimiento, hora de Colombia. Así evito la ambigüedad de los fracciones de segundo después de las 23:59:59.
Un pago parcial tardío deja la factura en PARTIAL. Solo el pago que la salda decide entre PAID y PAID_LATE. Es un supuesto, anótalo en DECISIONS.md.
CF-01 sigue literal: el residuo de 0,40 deja la factura en PARTIAL. Si quieres cambiarlo (saldo menor a 1 peso cuenta como pagada), es un cambio pequeño que hacemos después.