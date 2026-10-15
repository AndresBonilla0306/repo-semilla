import { useState } from "react";
import InvoicesPage from "./InvoicesPage";
import BatchPage from "./BatchPage";

export default function App() {
  const [tab, setTab] = useState<"facturas" | "lotes">("facturas");
  const tabClass = (active: boolean) =>
    `px-4 py-2 border-b-2 ${active ? "border-slate-800 font-semibold" : "border-transparent text-slate-500"}`;

  return (
    <div className="mx-auto max-w-6xl p-4">
      <h1 className="mb-4 text-2xl font-bold">Conciliación de pagos</h1>
      <nav className="mb-6 flex border-b">
        <button className={tabClass(tab === "facturas")} onClick={() => setTab("facturas")}>Facturas</button>
        <button className={tabClass(tab === "lotes")} onClick={() => setTab("lotes")}>Cargar lote</button>
      </nav>
      {tab === "facturas" ? <InvoicesPage /> : <BatchPage />}
    </div>
  );
}