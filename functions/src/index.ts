import * as admin from "firebase-admin";
import { getMessaging } from "firebase-admin/messaging";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import {
  CODE_TTL_MS,
  canSendRing,
  extractCode,
  generatePin,
  pruneRingAttempts,
  qrPayloadFor
} from "./domain";

admin.initializeApp();

const db = admin.firestore();
const region = "us-central1";

function requireUid(auth?: { uid: string }): string {
  if (!auth?.uid) {
    throw new HttpsError("unauthenticated", "Anonymous sign-in is required.");
  }
  return auth.uid;
}

function requireString(value: unknown, name: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${name} is required.`);
  }
  return value.trim();
}

export const registerDevice = onCall({ region }, async (request) => {
  const uid = requireUid(request.auth);
  const fcmToken = requireString(request.data?.fcmToken, "fcmToken");

  await db.collection("devices").doc(uid).set(
    {
      fcmToken,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  return { ok: true };
});

export const createCode = onCall({ region }, async (request) => {
  const uid = requireUid(request.auth);
  const now = Date.now();
  const expiresAtMillis = now + CODE_TTL_MS;

  for (let attempt = 0; attempt < 5; attempt += 1) {
    const code = generatePin();
    const ref = db.collection("codes").doc(code);
    const created = await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(ref);
      if (snapshot.exists) {
        const existing = snapshot.data();
        const existingExpiry = existing?.expiresAt?.toMillis?.() ?? 0;
        if (existingExpiry > now && existing?.consumed !== true) {
          return false;
        }
      }
      transaction.set(ref, {
        receiverId: uid,
        consumed: false,
        createdAt: admin.firestore.Timestamp.fromMillis(now),
        expiresAt: admin.firestore.Timestamp.fromMillis(expiresAtMillis)
      });
      return true;
    });

    if (created) {
      return {
        code,
        qrPayload: qrPayloadFor(code),
        expiresAtMillis
      };
    }
  }

  throw new HttpsError("resource-exhausted", "Could not create a temporary code.");
});

export const redeemCode = onCall({ region }, async (request) => {
  const senderId = requireUid(request.auth);
  let code: string;
  try {
    code = extractCode(requireString(request.data?.codeOrPayload, "codeOrPayload"));
  } catch {
    throw new HttpsError("invalid-argument", "Enter a valid six digit code or QR payload.");
  }

  const now = Date.now();
  const codeRef = db.collection("codes").doc(code);

  return db.runTransaction(async (transaction) => {
    const codeSnapshot = await transaction.get(codeRef);
    if (!codeSnapshot.exists) {
      throw new HttpsError("not-found", "Code not found.");
    }

    const codeData = codeSnapshot.data() ?? {};
    const receiverId = codeData.receiverId as string | undefined;
    const expiresAtMillis = codeData.expiresAt?.toMillis?.() ?? 0;

    if (!receiverId) {
      throw new HttpsError("failed-precondition", "Code is invalid.");
    }
    if (receiverId === senderId) {
      throw new HttpsError("failed-precondition", "You cannot pair with your own code.");
    }
    if (codeData.consumed === true || expiresAtMillis <= now) {
      throw new HttpsError("deadline-exceeded", "Code has expired.");
    }

    const pairId = `${senderId}_${receiverId}`;
    const pairRef = db.collection("pairs").doc(pairId);
    const createdAt = admin.firestore.Timestamp.fromMillis(now);

    transaction.set(pairRef, {
      senderId,
      receiverId,
      status: "active",
      createdAt,
      updatedAt: createdAt
    });
    transaction.update(codeRef, {
      consumed: true,
      consumedBy: senderId,
      consumedAt: createdAt
    });

    return {
      pairId,
      senderId,
      receiverId,
      createdAtMillis: now
    };
  });
});

export const sendRing = onCall({ region }, async (request) => {
  const senderId = requireUid(request.auth);
  const pairId = requireString(request.data?.pairId, "pairId");
  const superRing = request.data?.superRing === true;
  const pairRef = db.collection("pairs").doc(pairId);
  const rateLimitRef = db.collection("rateLimits").doc(pairId);
  const now = Date.now();

  const ringContext = await db.runTransaction(async (transaction) => {
    const pairSnapshot = await transaction.get(pairRef);
    if (!pairSnapshot.exists) {
      throw new HttpsError("not-found", "Connection not found.");
    }
    const pair = pairSnapshot.data() ?? {};
    if (pair.senderId !== senderId || pair.status !== "active") {
      throw new HttpsError("permission-denied", "Connection cannot be ringed.");
    }

    const receiverId = pair.receiverId as string;
    const blockRef = db.collection("blocks").doc(`${receiverId}_${senderId}`);
    const [blockSnapshot, rateLimitSnapshot] = await Promise.all([
      transaction.get(blockRef),
      transaction.get(rateLimitRef)
    ]);

    if (blockSnapshot.exists) {
      throw new HttpsError("permission-denied", "This connection is blocked.");
    }

    const attempts = pruneRingAttempts(
      (rateLimitSnapshot.data()?.attempts as number[] | undefined) ?? [],
      now
    );
    if (!canSendRing(attempts, now)) {
      throw new HttpsError("resource-exhausted", "Too many rings. Try again later.");
    }

    const receiptRef = db.collection("ringReceipts").doc();
    const timestamp = admin.firestore.Timestamp.fromMillis(now);
    transaction.set(rateLimitRef, {
      pairId,
      attempts: [...attempts, now],
      updatedAt: timestamp
    });
    transaction.set(receiptRef, {
      pairId,
      senderId,
      receiverId,
      status: "sent",
      superRing,
      createdAt: timestamp,
      expiresAt: admin.firestore.Timestamp.fromMillis(now + CODE_TTL_MS)
    });

    return { receiverId, receiptId: receiptRef.id };
  });

  const receiver = await db.collection("devices").doc(ringContext.receiverId).get();
  const fcmToken = receiver.get("fcmToken") as string | undefined;
  let pushed = false;
  if (fcmToken) {
    // The receipt is already saved, so an open app still sees the ring even if push fails.
    pushed = await getMessaging()
      .send({
        token: fcmToken,
        data: {
          type: "ring",
          receiptId: ringContext.receiptId,
          pairId,
          superRing: superRing ? "true" : "false"
        },
        android: {
          priority: "high",
          ttl: CODE_TTL_MS
        }
      })
      .then(() => true)
      .catch(() => false);
  }

  return { ok: true, receiptId: ringContext.receiptId, pushed };
});

export const respondToRing = onCall({ region }, async (request) => {
  const receiverId = requireUid(request.auth);
  const receiptId = requireString(request.data?.receiptId, "receiptId");
  const response = requireString(request.data?.response, "response");
  if (!["accepted", "ignored"].includes(response)) {
    throw new HttpsError("invalid-argument", "response must be accepted or ignored.");
  }

  const receiptRef = db.collection("ringReceipts").doc(receiptId);
  const receiptSnapshot = await receiptRef.get();
  if (!receiptSnapshot.exists) {
    throw new HttpsError("not-found", "Ring receipt not found.");
  }
  const receipt = receiptSnapshot.data() ?? {};
  if (receipt.receiverId !== receiverId) {
    throw new HttpsError("permission-denied", "Only the receiver can respond.");
  }

  // One answer covers every unanswered ring from the same person, so stacked rings clear together.
  const pendingFromSamePerson = await db
    .collection("ringReceipts")
    .where("receiverId", "==", receiverId)
    .where("pairId", "==", receipt.pairId)
    .where("status", "==", "sent")
    .get();
  const respondedAt = admin.firestore.FieldValue.serverTimestamp();
  const batch = db.batch();
  batch.update(receiptRef, { status: response, respondedAt });
  pendingFromSamePerson.docs
    .filter((doc) => doc.id !== receiptId)
    .forEach((doc) => batch.update(doc.ref, { status: response, respondedAt }));
  await batch.commit();

  const sender = await db.collection("devices").doc(receipt.senderId as string).get();
  const senderToken = sender.get("fcmToken") as string | undefined;
  if (senderToken) {
    await getMessaging().send({
      token: senderToken,
      data: {
        type: "ring_response",
        receiptId,
        response
      },
      android: {
        priority: "normal"
      }
    });
  }

  return { ok: true };
});

export const deletePair = onCall({ region }, async (request) => {
  const uid = requireUid(request.auth);
  const pairId = requireString(request.data?.pairId, "pairId");
  const pairRef = db.collection("pairs").doc(pairId);
  const snapshot = await pairRef.get();
  if (!snapshot.exists) {
    return { ok: true };
  }
  const pair = snapshot.data() ?? {};
  if (pair.senderId !== uid && pair.receiverId !== uid) {
    throw new HttpsError("permission-denied", "Connection cannot be deleted.");
  }

  await pairRef.update({
    status: "deleted",
    deletedBy: uid,
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });
  return { ok: true };
});

export const blockPair = onCall({ region }, async (request) => {
  const uid = requireUid(request.auth);
  const pairId = requireString(request.data?.pairId, "pairId");
  const pairRef = db.collection("pairs").doc(pairId);
  const snapshot = await pairRef.get();
  if (!snapshot.exists) {
    throw new HttpsError("not-found", "Connection not found.");
  }
  const pair = snapshot.data() ?? {};
  if (pair.senderId !== uid && pair.receiverId !== uid) {
    throw new HttpsError("permission-denied", "Connection cannot be blocked.");
  }

  const blockerId = uid;
  const blockedId = pair.senderId === uid ? (pair.receiverId as string) : (pair.senderId as string);
  await db.runTransaction(async (transaction) => {
    transaction.set(db.collection("blocks").doc(`${blockerId}_${blockedId}`), {
      blockerId,
      blockedId,
      pairId,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    transaction.update(pairRef, {
      status: "blocked",
      blockedBy: blockerId,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
  });

  return { ok: true };
});

export const unblockPair = onCall({ region }, async (request) => {
  const uid = requireUid(request.auth);
  const pairId = requireString(request.data?.pairId, "pairId");
  const pairRef = db.collection("pairs").doc(pairId);
  const snapshot = await pairRef.get();
  if (!snapshot.exists) {
    throw new HttpsError("not-found", "Connection not found.");
  }
  const pair = snapshot.data() ?? {};
  if (pair.blockedBy !== uid) {
    throw new HttpsError("permission-denied", "Only the person who blocked can unblock.");
  }

  const blockedId = pair.senderId === uid ? (pair.receiverId as string) : (pair.senderId as string);
  await db.runTransaction(async (transaction) => {
    transaction.delete(db.collection("blocks").doc(`${uid}_${blockedId}`));
    transaction.update(pairRef, {
      status: "active",
      blockedBy: admin.firestore.FieldValue.delete(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
  });

  return { ok: true };
});
