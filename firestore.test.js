const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validNow() {
  return new Date(Date.now() - 1000);
}

test("Unauthenticated user cannot read user node or envelopes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("envelopes").get());
});

test("Authenticated user can create and read their own UserNode", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      handle: "@alice_stealth",
      phoneHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      publicKeyHex: "8f40c5adb68f25624ae5b214ea767a6ec94d829d3d7b5e1ad1ba6f3e2138285f",
      activeCipherId: "AEGIS-52-CASCADE",
      customServerUrl: "wss://relay.aegis52.local:8443",
      createdAt: validNow(),
      updatedAt: validNow(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Cross-user access: Bob cannot read or modify Alice's UserNode or envelopes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("envelopes").doc("env_01").set({
      userId: ALICE_UID,
      conversationId: "conv_secret_1",
      senderHandle: "@alice_stealth",
      cipherSuite: "AEGIS-52-CASCADE",
      ephemeralPubKey: "a1b2c3d4e5f60718293a4b5c6d7e8f90",
      nonceHex: "00112233445566778899aabb",
      ciphertextHex: "9f8e7d6c5b4a39281706f5e4d3c2b1a0",
      macTagHex: "11223344556677889900aabbccddeeff",
      transportRoute: "LAN_P2P",
      createdAt: validNow(),
      updatedAt: validNow(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("envelopes").doc("env_01").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("envelopes").get());
});

test("Shadow field injection and invalid transportRoute are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      handle: "@alice_stealth",
      phoneHash: "e3b0c44298fc1c149afbf4c8996fb924",
      publicKeyHex: "8f40c5adb68f25624ae5b214ea767a6e",
      activeCipherId: "AEGIS-52-CASCADE",
      isVerifiedAdmin: true,
      createdAt: validNow(),
      updatedAt: validNow(),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("envelopes").doc("env_bad").set({
      userId: ALICE_UID,
      conversationId: "conv_secret_1",
      senderHandle: "@alice_stealth",
      cipherSuite: "AEGIS-52-CASCADE",
      ephemeralPubKey: "a1b2c3d4e5f60718293a4b5c6d7e8f90",
      nonceHex: "00112233445566778899aabb",
      ciphertextHex: "9f8e7d6c5b4a39281706f5e4d3c2b1a0",
      macTagHex: "11223344556677889900aabbccddeeff",
      transportRoute: "INVALID_ROUTE",
      createdAt: validNow(),
      updatedAt: validNow(),
    })
  );
});
