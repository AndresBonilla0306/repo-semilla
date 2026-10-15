#!/bin/sh
# Carga las facturas de seed/out/facturas.csv si la tabla esta vacia.
set -e

if [ ! -f /seed/facturas.csv ]; then
  echo "[seed-loader] No se encontro /seed/facturas.csv: el sistema queda sin facturas."
  echo "[seed-loader] Coloca los CSV en seed/out/ y ejecuta: docker compose run --rm seed-loader"
  exit 0
fi

COUNT=$(psql -tA -c "select count(*) from invoice")
if [ "$COUNT" -gt 0 ]; then
  echo "[seed-loader] La tabla invoice ya tiene $COUNT filas: no se recarga."
  exit 0
fi

echo "[seed-loader] Cargando facturas (puede tardar unos segundos)..."
psql -v ON_ERROR_STOP=1 -f /load/load-facturas.sql
psql -c "analyze invoice"
echo "[seed-loader] Listo: $(psql -tA -c 'select count(*) from invoice') facturas."