/** Formato colombiano sobre el texto del backend: nunca pasa por number, asi no pierde precision. */
export function formatCop(value: string): string {
  const [integer, decimals = ""] = value.split(".");
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `$ ${grouped},${decimals.padEnd(2, "0")}`;
}