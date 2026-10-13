# Sistema de Conciliación de Pagos

Prueba técnica **Ingeniero Senior Fullstack (Java / React)**. El sistema recibe pagos de Tesorería, los aplica contra facturas de proveedores y rechaza de forma explícita lo que no cuadra.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)

## Contenido

- [Estado del proyecto](#estado-del-proyecto)
- [Requisitos](#requisitos)
- [Puesta en marcha](#puesta-en-marcha)
- [Puertos y URL de entrada](#puertos-y-url-de-entrada)
- [Pruebas](#pruebas)
- [API](#api)
- [Arquitectura](#arquitectura)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Documentación adicional](#documentación-adicional)

## Estado del proyecto

| Área | Estado |
|---|---|
| Módulo heredado (`legacy/`): caracterización, corrección y explicación | ✅ Hecho |
| Dominio: dinero, facturas, pagos, rechazos (RF-01, 02, 04 a 09, 11) | ✅ Hecho |
| Referencia única de pago (RF-12) y concurrencia sobre una factura (CF-04) | ✅ Hecho |
| API de pagos y consulta paginada de facturas (filtros por NIT y estado) | ✅ Hecho |
| Lotes JSON/CSV, reenvíos, auditoría, zona horaria, tablero | ❌ Fuera de alcance |
| Frontend | ❌ Fuera de alcance |
| Backend y frontend dentro de `docker compose up` | ❌ Pendiente |

El detalle de lo recortado y el porqué está en [`LIMITACIONES.md`](LIMITACIONES.md).

## Requisitos

- Docker Desktop, con el motor en ejecución
- JDK 21
- Maven 3.9+ (o el wrapper `mvnw` incluido)

## Puesta en marcha

> **Hoy `docker compose up` levanta únicamente la base de datos.** El backend se ejecuta con Maven y la carga de facturas es un paso manual.

Desde la raíz del repositorio, en PowerShell:

**1. Base de datos** (espera a que quede `healthy`)

```powershell
docker compose up -d
```

**2. Backend** (Flyway crea el esquema al arrancar)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

**3. Cargar las 500.000 facturas** (desde otra terminal, ya con el backend iniciado al menos una vez)

```powershell
Get-Content seed\load-facturas.sql | docker compose exec -T db psql -U conciliacion -d conciliacion -v ON_ERROR_STOP=1
```

Los CSV deben estar en `seed/out/` (no se versionan). Para volver a un estado limpio:

```powershell
docker compose down -v
```

## Puertos y URL de entrada

| Servicio | Puerto | Cómo se levanta |
|---|---|---|
| PostgreSQL 16 | `5432` | `docker compose up -d` |
| Backend (API) | `8080` | `mvnw spring-boot:run` en `backend/` |

URL de entrada: <http://localhost:8080/api/v1/facturas>

## Pruebas

```powershell
# Backend (requiere Docker abierto: Testcontainers levanta su propio PostgreSQL)
cd backend
.\mvnw.cmd test

# Módulo heredado
cd ..\legacy
.\mvnw.cmd test
```

## API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/facturas?nit=&status=&page=&size=` | Consulta paginada. `status`: `PENDING`, `PARTIAL`, `PAID` |
| `POST` | `/api/v1/pagos` | Aplica un pago |

Los montos viajan como **string** para no perder precisión.

**Ejemplo de pago**

```json
{
  "reference": "P-000001",
  "invoiceNumber": "FV-0000001",
  "amount": "1000",
  "paidAt": "2026-03-15T10:00:00-05:00"
}
```

**Respuestas**

| Código | Cuándo | Cuerpo |
|---|---|---|
| `200` | Pago aplicado | `status`, `previousBalance`, `balance` |
| `422` | Pago rechazado | `status`, `reason`, `detail` |
| `400` | Solicitud mal formada (por ejemplo, un monto con decimales) | `error`, `detail` |

Motivos de rechazo: `INVOICE_NOT_FOUND`, `AMOUNT_EXCEEDS_BALANCE`, `DUPLICATE_REFERENCE`.

## Arquitectura

Hexagonal: las dependencias apuntan hacia adentro y el dominio no conoce a Spring ni a JPA.

```
infrastructure  ──►  application  ──►  domain
(web, JPA,           (casos de uso      (Money, Invoice,
 transacción,         y puertos)         Payment)
 configuración)
```

- Los montos usan `BigDecimal` con 2 decimales exactos y `NUMERIC(19,2)` en la base.
- El bloqueo `SELECT ... FOR UPDATE` serializa los pagos concurrentes sobre una misma factura.
- La transacción la abre un decorador en `infrastructure/`, de modo que el caso de uso queda sin anotaciones de Spring.

## Estructura del repositorio

```
backend/   Aplicación Spring Boot (domain, application, infrastructure)
legacy/    Importador heredado de CSV: caracterización, copia original y versión corregida
seed/      Script de carga y datos de prueba (seed/out no se versiona)
```

## Documentación adicional

| Archivo | Contenido |
|---|---|
| [`DECISIONS.md`](DECISIONS.md) | Decisiones técnicas, alternativas descartadas y ambigüedades del enunciado |
| [`LIMITACIONES.md`](LIMITACIONES.md) | Qué quedó fuera y por qué |
| [`AI-USAGE.md`](AI-USAGE.md) | Uso de inteligencia artificial y correcciones aplicadas |