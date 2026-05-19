import { describe, expect, it } from "vitest";
import {
  canSendRing,
  extractCode,
  generatePin,
  isExpired,
  pruneRingAttempts,
  qrPayloadFor
} from "./domain";

describe("domain", () => {
  it("generates a six digit pin", () => {
    expect(generatePin(() => 0)).toBe("100000");
    expect(generatePin(() => 0.999999)).toBe("999999");
  });

  it("extracts plain and qr payload codes", () => {
    expect(extractCode("123456")).toBe("123456");
    expect(extractCode(qrPayloadFor("654321"))).toBe("654321");
  });

  it("rejects invalid code formats", () => {
    expect(() => extractCode("abc")).toThrow("invalid-code-format");
    expect(() => extractCode("12345")).toThrow("invalid-code-format");
  });

  it("checks expiry", () => {
    expect(isExpired(1000, 1000)).toBe(true);
    expect(isExpired(1001, 1000)).toBe(false);
  });

  it("prunes and limits ring attempts", () => {
    const now = 10 * 60 * 1000;
    expect(pruneRingAttempts([0, now - 1, now - 1000], now)).toEqual([now - 1, now - 1000]);
    expect(canSendRing([now - 1, now - 2, now - 3], now)).toBe(false);
    expect(canSendRing([now - 1, now - 2], now)).toBe(true);
  });
});
