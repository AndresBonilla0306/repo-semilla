# Sistema de Conciliación de Pagos

Prueba técnica **Ingeniero Senior Fullstack (Java / React)**. Recibe lotes de pago de Tesorería, los aplica contra facturas de proveedores y rechaza de forma explícita lo que no cuadra.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![React](https://img.shields.io/badge/React-18-61DAFB)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)

## Puesta en marcha

Requisito: Docker Desktop con el motor en ejecución. Los CSV de datos deben estar en `seed/out/` (no se versionan). Desde la raíz:

```powershell
docker compose up --build
```

> La primera construcción puede tardar **10 a 15 minutos** (descarga de dependencias de Maven y npm). Las siguientes usan caché.

Orden de arranque: base de datos, backend (Flyway crea el esquema), carga de las 500.000 facturas y frontend. Si `seed/out/facturas.csv` no existe, el sistema arranca sin facturas y lo indica en el log de `seed-loader`.

| Servicio | Puerto | URL |
|---|---|---|
| Frontend | 3000 | http://localhost:3000 **(entrada)** |
| API | 8080 | http://localhost:8080/api/v1/facturas |
| PostgreSQL | 5432 | `conciliacion` / `conciliacion` |

Estado limpio: `docker compose down -v`.

## Pruebas

```powershell
cd backend ; .\mvnw.cmd test     # requiere Docker abierto (Testcontainers)
cd ..\legacy ; .\mvnw.cmd test
cd ..\frontend ; npm test
```

## API

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/facturas` | Filtros: `nit`, `status`, `dueFrom`, `dueTo`, `balanceMin`, `balanceMax`, `page`, `size` |
| POST | `/api/v1/pagos` | Aplica un pago |
| POST | `/api/v1/lotes` | Lote en JSON |
| POST | `/api/v1/lotes/archivo` | Lote en CSV (multipart, campo `file`) |
| GET | `/api/v1/lotes/{id}` | Resultado paginado, filtro `reason` |
| GET | `/api/v1/lotes/{id}/resultado` | Descarga del resultado en CSV |
| GET | `/api/v1/conciliacion/tablero` | Totales y top 10 de proveedores (`dueFrom`, `dueTo`) |

Los montos viajan como **string**. Respuestas: `200/201` aplicado, `422` rechazado (con `reason` y `detail`), `400` solicitud inválida, `409` conflicto de lote, `404` lote inexistente.

## Estructura

```
backend/    Spring Boot, arquitectura hexagonal (domain, application, infrastructure)
frontend/   React + TypeScript + Vite + Tailwind, servido por nginx
legacy/     Importador heredado: caracterización, copia original y versión corregida
seed/       Scripts de carga y medición (seed/out no se versiona)
```

## Estado

Implementado: módulo heredado, dominio y reglas de pago, auditoría, lotes idempotentes, filtros, tablero (API), frontend de facturas y lotes. **Fuera de alcance o incumplido** (detalle y diagnóstico en `LIMITACIONES.md`): PR-02 (lote de 50.000 líneas en 215,8 s frente a 60 s), tablero en la interfaz, barra de avance.

## Documentación

- [`DECISIONS.md`](DECISIONS.md): decisiones, alternativas descartadas y ambigüedades
- [`LIMITACIONES.md`](LIMITACIONES.md): qué quedó fuera, qué está mal y qué haría con dos días más
- [`AI-USAGE.md`](AI-USAGE.md): uso de IA y correcciones