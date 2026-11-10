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

/**
 * One pair document per two people, whoever scanned whom. Sorting the ids means
 * A scanning B and B scanning A land on the same connection (pairs are two-way).
 */
export function pairIdFor(a: string, b: string): string {
  return a < b ? `${a}_${b}` : `${b}_${a}`;
}

/** The other person in a pair, or null when uid is not part of it. */
export function otherParty(
  pair: { senderId?: unknown; receiverId?: unknown },
  uid: string
): string | null {
  if (pair.senderId === uid && typeof pair.receiverId === "string") return pair.receiverId;
  if (pair.receiverId === uid && typeof pair.senderId === "string") return pair.senderId;
  return null;
}

/** Rate limits are per direction, so one side ringing a lot never locks out the other. */
export function rateLimitKey(pairId: string, ringerId: string): string {
  return `${pairId}__${ringerId}`;
}

export type RedeemBlockAction = "reject" | "clear-own-block" | "none";

/**
 * Re-pairing rules for blocks left over from an earlier connection:
 * - the code owner blocked the scanner: refuse (a fresh code must not bypass a block);
 * - the scanner blocked the code owner: scanning again is a clear "reconnect", so lift it.
 */
export function redeemBlockAction(ownerBlockedScanner: boolean, scannerBlockedOwner: boolean): RedeemBlockAction {
  if (ownerBlockedScanner) return "reject";
  if (scannerBlockedOwner) return "clear-own-block";
  return "none";
}
