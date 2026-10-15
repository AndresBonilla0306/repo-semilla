import { describe, expect, it } from "vitest";
import { formatCop } from "./format";

describe("formatCop", () => {
  it("usa punto para miles y coma para decimales", () => {
    expect(formatCop("37500483.40")).toBe("$ 37.500.483,40");
  });
  it("completa los decimales", () => {
    expect(formatCop("1000")).toBe("$ 1.000,00");
  });
  it("no pierde precision en valores grandes", () => {
    expect(formatCop("9007199254740993.01")).toBe("$ 9.007.199.254.740.993,01");
  });
});