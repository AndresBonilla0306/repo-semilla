# DECISIONS

Cada decisión indica la alternativa descartada y por qué. Cuando el enunciado era ambiguo o se contradecía, se registra el supuesto adoptado y si quedó implementado.

> Cartera no respondió las preguntas del enunciado, así que todos los supuestos son decisiones propias, no confirmaciones del negocio.

## 1. Ambigüedades del enunciado

| # | Ambigüedad | Estado |
|---|---|---|
| A1 | RF-04 (facturas con 2 decimales) vs RF-10 (pagos enteros) | Implementado (lectura literal); deja un problema abierto |
| A2 | RF-18 (aplicar en orden del lote) vs RF-19 (resultado independiente del orden) | Implementado |
| A3 | RF-12 (referencia no se aplica dos veces) vs RF-20 (reenvío normal) | Implementado |
| A4 | RF-13 (vencimiento en hora de Colombia) vs contenedor en UTC | Implementado |
| A5 | `PAGADA_EXTEMPORANEA` no aparece en la lista de estados de RF-03 | Implementado como `PAID_LATE` |
| A6 | RF-14: estado "calculado", ¿derivado o persistido? | Implementado: persistido |
| A7 | RF-15 "usuario" sin autenticación; RF-24 "total rechazado" | Implementado |
| A8 | RF-12: ¿una referencia rechazada queda "usada"? | Implementado |
| A9 | RF-24: "rango de fechas filtrado", ¿de qué fecha? | Implementado |

### A1. Residuo de centavos (RF-04 vs RF-10, CF-01 y CF-02)

- **Supuesto:** lectura literal. Un pago de 37.500.483 sobre un saldo de 37.500.483,40 se aplica y deja 0,40. Un pago de 37.500.484 excede el saldo y se rechaza completo (RF-09).
- **Problema que queda:** la factura queda `PARTIAL` con un saldo que Tesorería nunca podrá pagar, porque solo opera en pesos enteros.
- **Propuesta no implementada:** considerar `PAID` cuando el saldo sea menor que 1 peso, con un umbral explícito y documentado.
- **Alternativa descartada:** redondear el pago o el saldo. Es dinero inventado o perdido sin que nadie lo decida.

### A2. Orden de las líneas (RF-18 vs RF-19, CF-06)

- **Supuesto:** las líneas se aplican en el orden del lote (RF-18). Con saldo 100, el pago A de 60 se aplica y el B de 70 se rechaza. RF-19 se lee como determinismo: el mismo lote produce siempre el mismo resultado.
- **Alternativa descartada:** ordenar las líneas por un criterio fijo (por ejemplo, la referencia). Cumple RF-19 literalmente, pero contradice RF-18.
- **Costo:** el resultado depende de cómo Tesorería ordena el archivo.

### A3. Reenvío de lotes (RF-12 vs RF-20, CF-03)

- **Supuesto:** reenviar un lote no es un error. El lote se identifica por `loteId` y se guarda un hash SHA-256 de su contenido.
  - Mismo `loteId` y mismo hash: se devuelve el resultado guardado (200, `reenvio: true`) sin tocar saldos.
  - Mismo `loteId` y contenido distinto: 409.
  - Misma referencia en un lote distinto: la línea se rechaza como `DUPLICATE_REFERENCE`.
- **CSV sin `loteId`:** se deriva del hash del archivo (`CSV-<hash>`), así que reenviar el mismo archivo es idempotente.
- **Alternativa descartada:** rechazar todo reenvío como duplicado. Cumple RF-12, pero convierte una operación normal del negocio en una cascada de rechazos.

### A4. Zona horaria (RF-13 vs UTC, CF-05)

- **Supuesto:** el vencimiento se evalúa en `America/Bogota` con una zona explícita, nunca con la zona por defecto de la JVM. Un pago con fecha `2026-03-15T23:30:00-05:00` sobre una factura que vence el 2026-03-15 llega a tiempo. En UTC esa fecha sería `2026-03-16T04:30Z` y saldría como tardía.
- **"Aplicado después de ese instante":** se usa la `fechaPago` de la línea, no el momento en que el servidor procesa. Es la única lectura con la que CF-05 tiene sentido.
- **Límite:** un pago es tardío si cae desde las 00:00 del día siguiente al vencimiento, hora de Colombia. Equivale a "después de las 23:59:59" y evita la ambigüedad de las fracciones de segundo.

### A5. Estado `PAID_LATE` (RF-03 vs RF-13)

- **Supuesto:** es un cuarto estado, además de `PENDING`, `PARTIAL` y `PAID`.
- **No es derivable solo del saldo:** depende de la fecha del pago que llevó el saldo a cero. Solo el pago que salda la factura decide entre `PAID` y `PAID_LATE`; un pago parcial tardío deja la factura en `PARTIAL`.

### A6. Estado derivado o persistido (RF-14)

- **Decisión:** el estado se persiste en la columna `status`, con índice, y se actualiza en la misma transacción que el saldo. El dominio valida que el estado sea coherente con el saldo y rechaza datos corruptos.
- **Alternativa descartada:** derivarlo al leer. Con `PAID_LATE` ya no es posible, y filtrar por estado obligaba a un barrido completo de 500.000 filas.

### A7. Usuario fijo y "total rechazado" (RF-15, RF-24)

- **Usuario:** valor fijo `tesoreria`, configurable con `app.audit.user`, porque el enunciado excluye la autenticación.
- **"Total rechazado":** suma de los valores de los intentos rechazados que están en `payment_audit` y pertenecen a una factura del rango. Los rechazos por factura inexistente y las líneas `INVALID_LINE` no se cuentan, porque no pertenecen a ninguna factura.

### A8. Referencia de un pago rechazado (RF-12)

- **Decisión:** un pago rechazado no consume su referencia. Tesorería puede reenviarlo corregido con la misma referencia.
- **Por qué:** RF-12 habla de "aplicarse" dos veces, y un pago rechazado no se aplicó.
- **Riesgo:** conviene confirmar la lectura con el negocio.

### A9. Rango del tablero (RF-24)

- **Supuesto:** el rango filtra por **fecha de vencimiento de la factura**, igual que RF-23, para que lista y tablero coincidan.
- **Qué suma cada total:** facturado = suma de `total` de las facturas del rango; aplicado = suma de los pagos aplicados a esas facturas; rechazado = ver A7; top 10 = proveedores con mayor saldo pendiente.

## 2. Decisiones técnicas

### Dominio y pagos

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| `BigDecimal` con `RoundingMode.UNNECESSARY` en `Money` | `double`, o redondeo automático | Más de 2 decimales falla en vez de redondearse. Redondear en silencio es dinero perdido o inventado |
| `NUMERIC(19,2)` en PostgreSQL | `double precision` | Sin pérdida de precisión en la base (RF-05) |
| `Invoice` inmutable: `applyPayment` devuelve una factura nueva | Entidad mutable | La mutable es más corta, pero facilita errores de concurrencia (CF-04) |
| `Payment` con `Money`, no `long` | `long` para respetar RF-10 | RF-10 es una regla de entrada: se valida en el borde y el dominio mantiene una sola representación del dinero |
| Rechazo como valor (`PaymentResult` es un `sealed interface`) | Excepciones por rechazo | RF-21 pide reportar cada línea, y un lote de 50.000 líneas pagaría el costo de crear excepciones |
| `Clock` inyectado en el servicio | `Instant.now()` directo | El instante de la auditoría se puede fijar en las pruebas |

### Concurrencia y transacciones

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| Arquitectura hexagonal con puertos de entrada y salida | Capas clásicas con `@Service` | El dominio y los casos de uso no importan `org.springframework`, y se prueban sin base de datos |
| Decorador transaccional en `infrastructure/transaction/` | `@Transactional` en el servicio | Esto último es una línea, pero mete Spring en `application/`. El costo del decorador es una clase más |
| Bloqueo pesimista (`SELECT ... FOR UPDATE`) | Bloqueo optimista con `@Version` y reintentos | Los lotes concurrentes sobre una misma factura son un caso esperado (CF-04) y reintentar complica el código. Se acepta la espera. `version` queda como segunda defensa |
| Bloquear la factura antes de revisar la referencia | Confiar solo en la clave primaria y capturar la excepción | Con el bloqueo, la segunda petición espera, ve el pago de la primera y recibe un rechazo explicable. Con la excepción, la transacción hace rollback y entrega un error |
| Tres defensas para RF-12: chequeo en el servicio, bloqueo de la factura y clave primaria de `payment.reference` | Una sola | Si falla una capa, la siguiente evita el doble pago |
| **Una transacción por línea** del lote | Una transacción por lote entero | Un lote entero mantendría bloqueadas todas sus facturas hasta el final, y dos lotes que las tomen en distinto orden podrían bloquearse entre sí (deadlock). Costo: un fallo a mitad de lote lo deja a medias y es parte de la causa de PR-02 |
| Auditoría en la misma transacción que el saldo | Auditoría asíncrona o en transacción aparte | Una transacción aparte puede dejar saldo sin auditoría o auditoría sin saldo |
| `payment_audit` sin clave foránea ni unicidad | FK a `invoice` y referencia única | Se auditan facturas inexistentes y una misma referencia puede intentarse varias veces (A8) |
| `CHECK` de coherencia en `payment_audit` | Confiar solo en el código | Una fila `APPLIED` debe cumplir `saldo_posterior = saldo_anterior - valor`. Es una segunda defensa en la base |

### Lotes

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| Líneas "crudas" (strings) validadas por el servicio; JSON y CSV comparten una sola ruta | Validar al deserializar el JSON | Una línea mala se rechaza sola con motivo `INVALID_LINE` y no tumba el lote (RF-21) |
| `INVALID_LINE` como motivo de rechazo | Responder 400 al lote entero | Alinea el comportamiento con RF-21 |
| Parseo del CSV copiado de `legacy/` al backend | Depender del módulo `legacy` | `legacy/` es un build Maven aparte. Es duplicación consciente: si se corrige uno hay que corregir el otro |
| `JdbcTemplate` con inserción por tandas (1.000) para las líneas del lote | JPA con `save` por línea | Con 50.000 líneas, JPA hace un `INSERT` por fila |
| Procesamiento síncrono | Cola o proceso en segundo plano con avance | Es lo más simple. Costo: no hay avance real (RF-26) y un lote grande bloquea la petición |

### API

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| Montos como **string** en el JSON | Números JSON | JavaScript los lee como `double` y pierde precisión (RF-05) |
| RF-10 validado en el borde: `amount` solo acepta dígitos y responde 400 con decimales | Validarlo en el dominio | El dominio mantiene una sola representación del dinero |
| Rechazos con **422** y `reason` y `detail`. Solicitud mal formada: **400**. Conflicto de lote: **409**. Lote inexistente: **404** | 404 y 409 según el motivo del rechazo | Un único formato por línea se reutiliza en los lotes (RF-21) |
| Paginación con `OFFSET`, ordenada por `number` | Paginación por cursor | `number` es único e inmutable, así que aplicar pagos no mueve filas entre páginas (PR-03, parcial). El cursor es más estable en páginas profundas, pero cambia más cosas en el frontend |
| Filtros combinados con `Specification` de Spring Data | Una consulta por combinación | Los filtros opcionales se combinan con AND sin multiplicar métodos |
| Estados en inglés (`PENDING`, `PARTIAL`, `PAID`, `PAID_LATE`) | Estados en español | El enunciado pide identificadores en inglés |

### Base de datos

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| Esquema gestionado por Flyway y `ddl-auto: validate` | Que Hibernate cree las tablas | El esquema es versionado y reproducible |
| Carga de datos con `COPY` y tabla temporal | Insertar 500.000 filas con JPA | JPA tardaría minutos. `COPY` es el mecanismo nativo para carga masiva |
| Índices de consulta (`V2` y `V6`) creados después de la carga, con `ANALYZE` | Crear todos los índices desde `V1` | Insertar 500.000 filas con índices es más lento, y sin `ANALYZE` el planificador elige mal. Los índices compuestos terminan en `number` para que el orden de la paginación pueda usarlos |

### Entrega y frontend

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| Healthcheck del backend por puerto (`/dev/tcp`) | Spring Actuator | No agrega dependencias. El puerto abre después de que Flyway termina. Costo: un puerto abierto no garantiza que todo esté listo |
| `seed-loader` como servicio de un solo disparo, idempotente por conteo; si falta `facturas.csv`, avisa y termina bien | Fallar el arranque | Un segundo `up` no recarga 500.000 filas, y el sistema levanta vacío con un aviso claro |
| Pruebas omitidas al construir la imagen (`-DskipTests`) | Correr pruebas en el build | Testcontainers necesita Docker, que no está disponible dentro del build |
| nginx sirve el build y hace proxy de `/api` con `proxy_read_timeout` de 600 s | Servir con Vite o abrir CORS | El navegador habla con un solo origen. El timeout por defecto (60 s) cortaría un lote de 50.000 líneas, que tardó 215 s |
| Frontend sin librería de rutas ni de estado: `fetch`, `useState` y `URLSearchParams` | React Router, Redux o React Query | Son dos pantallas. Costo: la navegación y la caché son artesanales |
| Filtros y página de facturas en la URL | Estado local | RF-25: pegar el enlace en otra pestaña reproduce la vista |
| Formato de montos sobre el texto del backend, nunca con `number` | `Intl.NumberFormat` sobre `Number` | `Number` pierde precisión por encima de 2^53 |
| Testcontainers fijado por propiedad en `1.21.4` | Saltar a Testcontainers 2.x | La versión que gestiona Spring Boot 3.5 no se entendía con la API del Docker Desktop actual (respuesta 400 con campos vacíos). La 2.x renombra artefactos y cambiaría más cosas de las necesarias |

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
- **Rechazar el archivo entero solo si** está vacío, tiene encabezado inválido, es ilegible o supera 100.000 líneas con contenido. Todo lo demás se rechaza por línea.
- **Archivos no UTF-8:** se leen como Windows-1252 con advertencia. Rechazarlos cortaba el síntoma, pero castigaba a Tesorería por un problema de codificación. El riesgo es que un archivo en otra codificación se lea con caracteres erróneos.
- **Valores como `1.000` o `100.00`:** se rechazan con motivo. En pesos colombianos el punto es separador de miles, así que adivinar mal es dinero.
- **Número de línea:** físico, con el encabezado como línea 1.

## 4. Casos frontera (Anexo B)

| Caso | Resultado considerado correcto | Estado |
|---|---|---|
| CF-01 | El pago se aplica y la factura queda `PARTIAL` con 0,40 de saldo (A1) | Probado |
| CF-02 | Se rechaza completo con `AMOUNT_EXCEEDS_BALANCE` y el saldo no cambia | Probado |
| CF-03 | El reenvío devuelve el resultado guardado y los saldos quedan idénticos (A3) | Probado en servicio y por HTTP |
| CF-04 | La suma aplicada nunca supera el saldo original: un pago se aplica y el otro se rechaza | Probado con dos lotes concurrentes contra PostgreSQL real |
| CF-05 | La factura no queda extemporánea (A4) | Probado, incluida la frontera 04:59:59Z / 05:00:00Z |
| CF-06 | A se aplica y B se rechaza (A2) | Probado a nivel de lote |
| CF-07 | Carga de 50.000 líneas en menos de 60 s | **No cumple: 215,8 s.** Ver `LIMITACIONES.md` |
| CF-08 | La paginación no repite ni omite facturas | Probado sin filtros mientras se aplican pagos. Con filtros por estado o saldo no está garantizado |