# LIMITACIONES

Qué quedó fuera, qué está mal en esta solución y qué haría con dos días más.

## 1. No cumple

### PR-02: lote de 50.000 líneas (CF-07)

- **Medido:** 215,8 s contra un límite de 60 s, con la base sembrada y un solo cliente.
- **Hipótesis (no la medí):** cada línea abre su propia transacción con unas 6 consultas (bloqueo de la factura, chequeo de referencia, lectura antes de guardar, guardar factura, guardar pago y guardar auditoría). Son unos 4,3 ms por línea.


## 2. Qué está mal o es frágil

- **Un lote interrumpido queda en `PROCESSING` para siempre.** Lo vi en vivo: al cortar el backend a mitad de lote quedaron 33.168 pagos aplicados y ningún detalle de líneas, porque las líneas se guardan todas juntas al final. Reenviar ese lote responde 409. Falta un mecanismo de reanudación.
- **Un error inesperado no deja auditoría.** Si la transacción hace rollback (caída de la base, choque de la clave primaria de `payment.reference` entre facturas distintas), ese intento no queda auditado. Solo se auditan los resultados que decide el dominio.
- **Dos pagos con la misma referencia sobre facturas distintas no comparten bloqueo.** La defensa es la clave primaria, que lanza una excepción en vez de un rechazo limpio.
- **CF-01 deja un residuo.** Un pago de 37.500.483 sobre 37.500.483,40 deja la factura en `PARTIAL` con 0,40 que Tesorería nunca podrá pagar. Propuesta: tratar como pagada una factura con saldo menor a 1 peso.
- **`IllegalArgumentException` se traduce a 400 en bloque.** Un error de programación podría disfrazarse de error del cliente.
- **El parseo del CSV está duplicado** entre `legacy/` y el backend. Si se corrige uno hay que corregir el otro.
- **`readAllBytes` carga el archivo completo en memoria**, tanto en `legacy/` como al subir un CSV. El resultado de un lote también se descarga entero en memoria.
- **Las líneas `INVALID_LINE` no quedan en `payment_audit`** (no hay factura, valor ni fecha válidos que auditar), solo en el resultado del lote.
- **El tablero no cuenta** los rechazos por factura inexistente ni las líneas inválidas.

## 3. Rendimiento

- **PR-01 cumple:** con 500.000 facturas, p95 máximo de 54 ms (página 5000) contra 300 ms.
- **Límites de esa medición:** un solo cliente secuencial, caché caliente, 40 muestras por caso, desde PowerShell. No medí la última página (`OFFSET` degrada con la profundidad) ni carga concurrente.
- **PR-03 vale solo sin filtros mutables.** La paginación ordena por `number`, que no cambia al aplicar pagos, y hay una prueba sin filtros. Con un filtro por estado o saldo, una factura puede salir del filtro mientras se navega y desplazar las páginas. Cerrarlo exige paginación por cursor.
- **PR-04 no está medido.** La tabla pagina de a 50 filas, así que debería ser utilizable con el filtro más amplio, pero no hay virtualización ni medición.

## 4. Fuera de alcance

- **Tablero en la interfaz (RF-30).** El endpoint existe (`/api/v1/conciliacion/tablero`) y se prueba por API, pero no hay pantalla.
- **Avance real del procesamiento (RF-26).** El lote es síncrono y la interfaz solo muestra "Procesando…".
- **El estado de la pantalla de lotes no está en la URL.** Recargar la pierde. Reenviar el mismo lote devuelve el resultado guardado sin tocar saldos.
- **No hay endpoint para crear facturas (RF-01).** Entran por la carga masiva.
- **No hay endpoint para consultar la auditoría.**
- **Pruebas del frontend:** solo el formato de montos. No hay pruebas con Testing Library.
- **Si `seed/out/facturas.csv` no existe**, el sistema arranca sin facturas y lo avisa en el log de `seed-loader`.
- **La primera construcción de las imágenes tarda 10 a 15 minutos** (descarga de dependencias).
- **Sin autenticación,** como permite el enunciado. El usuario de la auditoría es fijo.

