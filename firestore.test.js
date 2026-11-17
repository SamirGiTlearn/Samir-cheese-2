const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";
const INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 1500) {
  return new Date(Date.now() - msOffset);
}

function makeCreateRoomPayload(roomCode, hostId, now = pastDate()) {
  return {
    roomCode,
    hostId,
    creatorColor: "WHITE",
    whitePlayerId: hostId,
    blackPlayerId: "",
    whiteName: "Samir",
    blackName: "",
    whitePhotoUrl: "",
    blackPhotoUrl: "",
    participantIds: [hostId],
    status: "WAITING",
    fen: INITIAL_FEN,
    turn: "WHITE",
    moveCount: 0,
    lastMoveUci: "",
    lastMoveSan: "",
    uciHistory: [],
    sanHistory: [],
    timerMinutes: 5,
    whiteTimeMs: 300000,
    blackTimeMs: 300000,
    winner: "",
    termination: "",
    drawOfferBy: "",
    rematchRoomCode: "",
    whiteLastSeenMs: Date.now() - 1000,
    blackLastSeenMs: 0,
    expiresAtMs: Date.now() + 86400000,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write users or rooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").doc("ABC123").get());
  await assertFails(unauthDb.collection("rooms").get());
  await assertFails(
    unauthDb
      .collection("rooms")
      .doc("ABC123")
      .set(makeCreateRoomPayload("ABC123", ALICE_UID))
  );
});

test("UserProfile CRUD: owner succeeds, cross-user & ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Samir",
      photoUrl: "",
      wins: 0,
      losses: 0,
      draws: 0,
      gamesPlayed: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      wins: 1,
      gamesPlayed: 1,
      updatedAt: pastDate(500),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Ghost field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(100),
    })
  );

  // Bob cannot read or update Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      wins: 99,
      updatedAt: pastDate(100),
    })
  );
});

test("OnlineRoom lifecycle: create, join, strict turn-locked moves, end, terminal lock, and rematch", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // 1. Alice creates room CM7K9P as White
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("CM7K9P")
      .set(makeCreateRoomPayload("CM7K9P", ALICE_UID))
  );

  // Ghost @DocumentId `id` field rejected
  await assertFails(
    aliceDb
      .collection("rooms")
      .doc("CM7K9X")
      .set({
        id: "CM7K9X",
        ...makeCreateRoomPayload("CM7K9X", ALICE_UID),
      })
  );

  // 2. Bob joins WAITING room as Black
  await assertSucceeds(
    bobDb.collection("rooms").doc("CM7K9P").update({
      blackPlayerId: BOB_UID,
      blackName: "Opponent",
      blackPhotoUrl: "",
      participantIds: [ALICE_UID, BOB_UID],
      status: "ACTIVE",
      blackLastSeenMs: Date.now() - 500,
      updatedAt: pastDate(400),
    })
  );

  // Signed-in user checking a non-existent room code gets empty doc (not PERMISSION_DENIED)
  await assertSucceeds(charlieDb.collection("rooms").doc("NOPE99").get());

  // Charlie cannot read or join an already ACTIVE room
  await assertFails(charlieDb.collection("rooms").doc("CM7K9P").get());
  await assertFails(
    charlieDb.collection("rooms").doc("CM7K9P").update({
      blackPlayerId: CHARLIE_UID,
      status: "ACTIVE",
      updatedAt: pastDate(300),
    })
  );

  // 3. Turn lock: Black CANNOT move on White's turn!
  await assertFails(
    bobDb.collection("rooms").doc("CM7K9P").update({
      fen: "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
      turn: "BLACK",
      moveCount: 1,
      lastMoveUci: "e2e4",
      lastMoveSan: "e4",
      uciHistory: ["e2e4"],
      sanHistory: ["e4"],
      whiteTimeMs: 298000,
      updatedAt: pastDate(250),
    })
  );

  // White moves e2e4 -> succeeds!
  await assertSucceeds(
    aliceDb.collection("rooms").doc("CM7K9P").update({
      fen: "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
      turn: "BLACK",
      moveCount: 1,
      lastMoveUci: "e2e4",
      lastMoveSan: "e4",
      uciHistory: ["e2e4"],
      sanHistory: ["e4"],
      whiteTimeMs: 298000,
      whiteLastSeenMs: Date.now() - 200,
      updatedAt: pastDate(200),
    })
  );

  // White CANNOT move twice in a row!
  await assertFails(
    aliceDb.collection("rooms").doc("CM7K9P").update({
      fen: "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
      turn: "WHITE",
      moveCount: 2,
      lastMoveUci: "e7e5",
      lastMoveSan: "e5",
      uciHistory: ["e2e4", "e7e5"],
      sanHistory: ["e4", "e5"],
      updatedAt: pastDate(150),
    })
  );

  // Black moves e7e5 -> succeeds!
  await assertSucceeds(
    bobDb.collection("rooms").doc("CM7K9P").update({
      fen: "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
      turn: "WHITE",
      moveCount: 2,
      lastMoveUci: "e7e5",
      lastMoveSan: "e5",
      uciHistory: ["e2e4", "e7e5"],
      sanHistory: ["e4", "e5"],
      blackTimeMs: 297000,
      blackLastSeenMs: Date.now() - 100,
      updatedAt: pastDate(100),
    })
  );

  // 4. Resignation ends game
  await assertSucceeds(
    bobDb.collection("rooms").doc("CM7K9P").update({
      status: "FINISHED",
      winner: "WHITE",
      termination: "RESIGNATION",
      drawOfferBy: "",
      updatedAt: pastDate(50),
    })
  );

  // 5. Terminal state locking: no moves allowed after FINISHED
  await assertFails(
    aliceDb.collection("rooms").doc("CM7K9P").update({
      fen: "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
      turn: "BLACK",
      moveCount: 3,
      lastMoveUci: "g1f3",
      lastMoveSan: "Nf3",
      uciHistory: ["e2e4", "e7e5", "g1f3"],
      sanHistory: ["e4", "e5", "Nf3"],
      updatedAt: pastDate(20),
    })
  );

  // Rematch room code can be set once
  await assertSucceeds(
    aliceDb.collection("rooms").doc("CM7K9P").update({
      rematchRoomCode: "RM9X2Q",
      updatedAt: pastDate(10),
    })
  );

  // Cannot overwrite rematchRoomCode once set
  await assertFails(
    bobDb.collection("rooms").doc("CM7K9P").update({
      rematchRoomCode: "OTHER1",
      updatedAt: pastDate(5),
    })
  );
});

test("OnlineRoom list query enforcement: participant filter succeeds, unfiltered fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("LIST01")
      .set(makeCreateRoomPayload("LIST01", ALICE_UID))
  );

  // Filtered query matching repository succeeds
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .where("participantIds", "array-contains", ALICE_UID)
      .get()
  );

  // Unfiltered query rejected
  await assertFails(charlieDb.collection("rooms").get());
});
