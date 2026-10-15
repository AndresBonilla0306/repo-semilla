export interface Invoice {
  number: string;
  supplierNit: string;
  supplierName: string;
  issueDate: string;
  dueDate: string;
  total: string;
  balance: string;
  status: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LineResponse {
  numeroLinea: number;
  referencia?: string | null;
  numeroFactura?: string | null;
  valor?: string | null;
  resultado: string;
  motivo?: string | null;
  detalle?: string | null;
}

export interface BatchResponse {
  loteId: string;
  origen: string;
  estado: string;
  totalLineas: number;
  aplicadas: number;
  rechazadas: number;
  reenvio: boolean;
  lineas: Page<LineResponse>;
}

/** Convierte cualquier respuesta no exitosa en un Error con el detalle del backend. */
export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    let detail = `Error ${response.status}`;
    try {
      const body = (await response.json()) as { detail?: string };
      if (body.detail) detail = body.detail;
    } catch {
      /* la respuesta no era JSON */
    }
    throw new Error(detail);
  }
  return (await response.json()) as T;
}