import { randomInt } from "crypto";

export const CODE_TTL_MS = 10 * 60 * 1000;
export const RATE_LIMIT_WINDOW_MS = 5 * 60 * 1000;
export const MAX_RINGS_PER_WINDOW = 3;

export function generatePin(random?: () => number): string {
  if (random) {
    return Math.floor(100000 + random() * 900000).toString();
  }
  return randomInt(100000, 1000000).toString();
}

export function qrPayloadFor(code: string): string {
  return `anonpager://redeem?code=${encodeURIComponent(code)}`;
}

export function extractCode(input: string): string {
  const trimmed = input.trim();
  if (/^\d{6}$/.test(trimmed)) {
    return trimmed;
  }

  try {
    const url = new URL(trimmed);
    const code = url.searchParams.get("code") ?? "";
    if (/^\d{6}$/.test(code)) {
      return code;
    }
  } catch {
    // Fall through to invalid input below.
  }

  throw new Error("invalid-code-format");
}

export function isExpired(expiresAtMillis: number, nowMillis: number): boolean {
  return expiresAtMillis <= nowMillis;
}

export function pruneRingAttempts(attempts: number[], nowMillis: number): number[] {
  return attempts.filter((timestamp) => nowMillis - timestamp < RATE_LIMIT_WINDOW_MS);
}

export function canSendRing(attempts: number[], nowMillis: number): boolean {
  return pruneRingAttempts(attempts, nowMillis).length < MAX_RINGS_PER_WINDOW;
}
