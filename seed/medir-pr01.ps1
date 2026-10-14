# Mide la latencia (ms) de la consulta de facturas por combinacion de filtros y reporta p95.
$base = "http://localhost:8080/api/v1/facturas"
$casos = [ordered]@{
  "sin filtros"          = "?size=50"
  "pagina profunda"      = "?page=5000&size=50"
  "nit"                  = "?nit=800404150-7"
  "estado PENDING"       = "?status=PENDING"
  "estado + nit"         = "?status=PENDING&nit=800404150-7"
  "rango vencimiento"    = "?dueFrom=2026-01-01&dueTo=2026-03-31"
  "rango amplio"         = "?dueFrom=2024-01-01&dueTo=2027-12-31"
  "rango saldo"          = "?balanceMin=1000000&balanceMax=5000000"
  "todos combinados"     = "?nit=800404150-7&status=PENDING&dueFrom=2025-01-01&dueTo=2027-12-31&balanceMin=1&balanceMax=99999999"
}
foreach ($c in $casos.GetEnumerator()) {
  Invoke-RestMethod ($base + $c.Value) | Out-Null   # calentamiento
  $t = 1..40 | ForEach-Object { (Measure-Command { Invoke-RestMethod ($base + $c.Value) | Out-Null }).TotalMilliseconds }
  $s = $t | Sort-Object
  "{0,-22} p50={1,6:N0} ms  p95={2,6:N0} ms" -f $c.Key, $s[19], $s[37]
}