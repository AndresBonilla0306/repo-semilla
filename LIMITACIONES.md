No hay frontend. RF-25 a 30 sin hacer.
Backend y frontend no están en docker-compose.yml. Hoy docker compose up solo levanta la base y la carga de facturas es manual.
Sin lotes (RF-16 a 22), sin idempotencia de reenvío (RF-20), sin auditoría (RF-15), sin tablero (RF-24).
Sin PAGADA_EXTEMPORANEA ni manejo de zona horaria (RF-13, CF-05). No hay prueba para el caso.
Rendimiento sin medir. El filtro por estado hace barrido completo y el count(*) de la paginación recorre las filas. Falta medir PR-01 y PR-03.
