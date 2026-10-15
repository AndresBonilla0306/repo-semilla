import { useEffect, useState, type FormEvent } from "react";
import { request, type Invoice, type Page } from "./api";
import { formatCop } from "./lib/format";

const FIELDS = ["nit", "status", "dueFrom", "dueTo", "balanceMin", "balanceMax"] as const;
const STATUSES = ["PENDING", "PARTIAL", "PAID", "PAID_LATE"];

type State =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ok"; data: Page<Invoice> };

export default function InvoicesPage() {
  // La URL es la fuente de verdad: pegar el enlace en otra pestana reproduce la vista (RF-25).
  const [params, setParams] = useState(() => new URLSearchParams(window.location.search));
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    const onPop = () => setParams(new URLSearchParams(window.location.search));
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setState({ kind: "loading" });
    const query = new URLSearchParams(params);
    query.set("size", "50");
    request<Page<Invoice>>(`/api/v1/facturas?${query.toString()}`, { signal: controller.signal })
      .then((data) => setState({ kind: "ok", data }))
      .catch((e: Error) => {
        if (e.name !== "AbortError") setState({ kind: "error", message: e.message });
      });
    return () => controller.abort();
  }, [params]);

  const navigate = (next: URLSearchParams) => {
    window.history.pushState(null, "", next.toString() ? `?${next.toString()}` : window.location.pathname);
    setParams(next);
  };

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const next = new URLSearchParams();
    for (const field of FIELDS) {
      const value = String(form.get(field) ?? "").trim();
      if (value) next.set(field, value);
    }
    navigate(next); // al cambiar filtros se vuelve a la pagina 0
  };

  const goToPage = (page: number) => {
    const next = new URLSearchParams(params);
    next.set("page", String(page));
    navigate(next);
  };

  const page = Number(params.get("page") ?? "0");

  return (
    <section>
      <form key={params.toString()} onSubmit={onSubmit} className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-6">
        <input name="nit" placeholder="NIT" defaultValue={params.get("nit") ?? ""} className="rounded border p-2" />
        <select name="status" defaultValue={params.get("status") ?? ""} className="rounded border p-2">
          <option value="">Todos los estados</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <input name="dueFrom" type="date" defaultValue={params.get("dueFrom") ?? ""} className="rounded border p-2" title="Vence desde" />
        <input name="dueTo" type="date" defaultValue={params.get("dueTo") ?? ""} className="rounded border p-2" title="Vence hasta" />
        <input name="balanceMin" inputMode="decimal" placeholder="Saldo mínimo" defaultValue={params.get("balanceMin") ?? ""} className="rounded border p-2" />
        <input name="balanceMax" inputMode="decimal" placeholder="Saldo máximo" defaultValue={params.get("balanceMax") ?? ""} className="rounded border p-2" />
        <button type="submit" className="col-span-2 rounded bg-slate-800 p-2 text-white md:col-span-6">Filtrar</button>
      </form>

      {state.kind === "loading" && <p className="p-6 text-center text-slate-500">Cargando facturas…</p>}
      {state.kind === "error" && (
        <p role="alert" className="rounded bg-red-50 p-4 text-red-700">No se pudo cargar: {state.message}</p>
      )}
      {state.kind === "ok" && state.data.items.length === 0 && (
        <p className="p-6 text-center text-slate-500">No hay facturas con esos filtros.</p>
      )}
      {state.kind === "ok" && state.data.items.length > 0 && (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b bg-slate-100">
                <tr>
                  <th className="p-2">Número</th>
                  <th className="p-2">NIT</th>
                  <th className="p-2">Proveedor</th>
                  <th className="p-2">Vence</th>
                  <th className="p-2 text-right">Total</th>
                  <th className="p-2 text-right">Saldo</th>
                  <th className="p-2">Estado</th>
                </tr>
              </thead>
              <tbody>
                {state.data.items.map((i) => (
                  <tr key={i.number} className="border-b">
                    <td className="p-2 font-mono">{i.number}</td>
                    <td className="p-2">{i.supplierNit}</td>
                    <td className="p-2">{i.supplierName}</td>
                    <td className="p-2">{i.dueDate}</td>
                    <td className="p-2 text-right tabular-nums">{formatCop(i.total)}</td>
                    <td className="p-2 text-right tabular-nums">{formatCop(i.balance)}</td>
                    <td className="p-2">{i.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex items-center justify-between text-sm">
            <button disabled={page <= 0} onClick={() => goToPage(page - 1)} className="rounded border px-3 py-1 disabled:opacity-40">
              Anterior
            </button>
            <span>
              Página {page + 1} de {state.data.totalPages.toLocaleString("es-CO")} ·{" "}
              {state.data.totalElements.toLocaleString("es-CO")} facturas
            </span>
            <button
              disabled={page + 1 >= state.data.totalPages}
              onClick={() => goToPage(page + 1)}
              className="rounded border px-3 py-1 disabled:opacity-40"
            >
              Siguiente
            </button>
          </div>
        </>
      )}
    </section>
  );
}