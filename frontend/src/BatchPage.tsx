import { useEffect, useRef, useState, type FormEvent } from "react";
import { request, type BatchResponse, type LineResponse, type Page } from "./api";
import { formatCop } from "./lib/format";

const REASONS = ["INVALID_LINE", "INVOICE_NOT_FOUND", "AMOUNT_EXCEEDS_BALANCE", "DUPLICATE_REFERENCE"];

export default function BatchPage() {
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false); // evita el doble envio aunque React no haya repintado todavia
  const [error, setError] = useState<string | null>(null);
  const [batch, setBatch] = useState<BatchResponse | null>(null);
  const [lines, setLines] = useState<Page<LineResponse> | null>(null);
  const [reason, setReason] = useState("");
  const [page, setPage] = useState(0);
  const [loadingLines, setLoadingLines] = useState(false);

  // Aviso si se intenta recargar mientras el lote se procesa (RF-27).
  useEffect(() => {
    if (!busy) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [busy]);

  const onSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (submitting.current) return;
    const form = new FormData(e.currentTarget);
    const file = form.get("file");
    const json = String(form.get("json") ?? "").trim();
    const hasFile = file instanceof File && file.size > 0;
    if (!hasFile && !json) {
      setError("Selecciona un archivo CSV o pega un JSON.");
      return;
    }
    submitting.current = true;
    setBusy(true);
    setError(null);
    setBatch(null);
    setLines(null);
    try {
      let response: BatchResponse;
      if (hasFile) {
        const body = new FormData();
        body.set("file", file);
        const loteId = String(form.get("loteId") ?? "").trim();
        if (loteId) body.set("loteId", loteId);
        response = await request<BatchResponse>("/api/v1/lotes/archivo", { method: "POST", body });
      } else {
        response = await request<BatchResponse>("/api/v1/lotes", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: json,
        });
      }
      setBatch(response);
      setLines(response.lineas);
      setReason("");
      setPage(0);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  };

  const loadLines = async (id: string, nextReason: string, nextPage: number) => {
    setLoadingLines(true);
    setError(null);
    try {
      const q = new URLSearchParams({ page: String(nextPage), size: "50" });
      if (nextReason) q.set("reason", nextReason);
      setLines((await request<BatchResponse>(`/api/v1/lotes/${encodeURIComponent(id)}?${q}`)).lineas);
      setReason(nextReason);
      setPage(nextPage);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoadingLines(false);
    }
  };

  return (
    <section>
      <form onSubmit={onSubmit} className="mb-6 space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <input type="file" name="file" accept=".csv" className="text-sm" />
          <input name="loteId" placeholder="ID del lote (opcional, solo CSV)" className="rounded border p-2 text-sm" />
        </div>
        <textarea
          name="json"
          rows={5}
          placeholder='o pega aquí el lote en JSON: {"loteId":"...","origen":"TESORERIA","pagos":[...]}'
          className="w-full rounded border p-2 font-mono text-sm"
        />
        <button type="submit" disabled={busy} className="rounded bg-slate-800 px-4 py-2 text-white disabled:opacity-50">
          {busy ? "Procesando…" : "Enviar lote"}
        </button>
        {busy && (
          <p className="text-sm text-slate-500">
            El lote se procesa de forma síncrona y un archivo grande puede tardar varios minutos. No cierres ni recargues la página.
          </p>
        )}
      </form>

      {error && <p role="alert" className="mb-4 rounded bg-red-50 p-4 text-red-700">{error}</p>}

      {batch && lines && (
        <div>
          <div className="mb-4 rounded border bg-slate-50 p-4 text-sm">
            <p className="font-semibold">Lote {batch.loteId} · {batch.estado}</p>
            <p>
              {batch.totalLineas.toLocaleString("es-CO")} líneas · {batch.aplicadas.toLocaleString("es-CO")} aplicadas ·{" "}
              <span className="font-semibold text-red-700">{batch.rechazadas.toLocaleString("es-CO")} rechazadas</span>
            </p>
            {batch.reenvio && (
              <p className="mt-1 text-amber-700">Este lote ya estaba procesado: se muestra el resultado guardado, sin tocar saldos.</p>
            )}
            <a href={`/api/v1/lotes/${encodeURIComponent(batch.loteId)}/resultado`} className="mt-2 inline-block text-blue-700 underline">
              Descargar resultado completo (CSV)
            </a>
          </div>

          <label className="mb-3 block text-sm">
            Filtrar por motivo:{" "}
            <select value={reason} onChange={(e) => void loadLines(batch.loteId, e.target.value, 0)} className="rounded border p-1">
              <option value="">Todas las líneas</option>
              {REASONS.map((r) => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
          </label>

          {loadingLines && <p className="p-4 text-slate-500">Cargando líneas…</p>}
          {!loadingLines && lines.items.length === 0 && <p className="p-4 text-slate-500">No hay líneas con ese filtro.</p>}
          {!loadingLines && lines.items.length > 0 && (
            <>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b bg-slate-100">
                    <tr>
                      <th className="p-2">#</th>
                      <th className="p-2">Referencia</th>
                      <th className="p-2">Factura</th>
                      <th className="p-2 text-right">Valor</th>
                      <th className="p-2">Resultado</th>
                      <th className="p-2">Motivo</th>
                    </tr>
                  </thead>
                  <tbody>
                    {lines.items.map((l) => (
                      <tr key={l.numeroLinea} className={`border-b ${l.resultado === "REJECTED" ? "bg-red-50" : ""}`}>
                        <td className="p-2">{l.numeroLinea}</td>
                        <td className="p-2 font-mono">{l.referencia}</td>
                        <td className="p-2 font-mono">{l.numeroFactura}</td>
                        <td className="p-2 text-right tabular-nums">
                          {l.valor && /^\d+(\.\d+)?$/.test(l.valor) ? formatCop(l.valor) : l.valor}
                        </td>
                        <td className="p-2">{l.resultado}</td>
                        <td className="p-2" title={l.detalle ?? ""}>{l.motivo}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="mt-4 flex items-center justify-between text-sm">
                <button disabled={page <= 0} onClick={() => void loadLines(batch.loteId, reason, page - 1)} className="rounded border px-3 py-1 disabled:opacity-40">
                  Anterior
                </button>
                <span>Página {page + 1} de {Math.max(lines.totalPages, 1)}</span>
                <button disabled={page + 1 >= lines.totalPages} onClick={() => void loadLines(batch.loteId, reason, page + 1)} className="rounded border px-3 py-1 disabled:opacity-40">
                  Siguiente
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </section>
  );
}