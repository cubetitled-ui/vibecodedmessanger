# Security Specification — Aegis-52 Zero-Knowledge Relay

## 1. Data Invariants
1. **User Isolation (`UserNode`)**: A `/users/{userId}` document can only be created, read, listed, updated, or deleted by the authenticated user whose `request.auth.uid == userId`, and `data.userId == request.auth.uid`.
2. **Envelope Isolation (`EncryptedEnvelope`)**: A `/users/{userId}/envelopes/{envelopeId}` document can only be created, read, listed, updated, or deleted when `request.auth.uid == userId` and `data.userId == request.auth.uid`.
3. **Immutable Creation Metadata**: Both `userId` and `createdAt` are strictly immutable on update (`incoming().userId == existing().userId && incoming().createdAt == existing().createdAt`).
4. **Strict Key Allowlisting & Size Bounds**: Every string field enforces strict `.size()` bounds and `incoming().keys().hasOnly(...)` blocks shadow fields.

## 2. The "Dirty Dozen" Payloads
1. Unauthenticated read on `/users/alice_123` -> Denied
2. Cross-user read by `bob_456` on `/users/alice_123` -> Denied
3. Shadow field injection (`"isAdmin": true`) on `/users/alice_123` -> Denied
4. Identity spoofing (`userId: "bob_456"` inside `/users/alice_123`) -> Denied
5. Oversized `handle` (> 60 chars) on `/users/alice_123` -> Denied
6. Mutating `createdAt` during update on `/users/alice_123` -> Denied
7. Unauthenticated read/list on `/users/alice_123/envelopes` -> Denied
8. Cross-user envelope read by `bob_456` on `/users/alice_123/envelopes/env_1` -> Denied
9. Invalid `transportRoute` enum (`"BLUETOOTH_HACK"`) on `/users/alice_123/envelopes/env_1` -> Denied
10. Missing required cryptographic fields (`macTagHex`) on envelope create -> Denied
11. Unauthorized field mutation (`ciphertextHex`) on envelope update -> Denied
12. Poisoned document ID (> 128 chars or invalid characters) -> Denied
