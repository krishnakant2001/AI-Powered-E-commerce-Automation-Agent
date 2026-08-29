# Agent Service — API Data Flow Reference

> Per-endpoint request → internal flow → response breakdown.
> Companion to [ARCHITECTURE.md](./ARCHITECTURE.md).

**Base URL:** `http://localhost:9060/agent` (direct) or via gateway `http://localhost:8080/agent`

**Required headers on every request** (injected by the API Gateway after JWT validation):

```http
X-user-id: 42
X-user-role: USER
X-user-email: user@example.com
```

**Universal response envelope** — `ApiResponse<T>` (nulls omitted):

```json
{ "success": true, "message": "...", "data": { }, "timestamp": "2026-08-29T10:15:30" }
```

---

## Endpoint Index

| # | Method | Path | Status | Section |
|---|---|---|---|---|
| 1 | `POST` | `/agent/chat` | 200 | [→](#1-post-agentchat) |
| 2 | `POST` | `/agent/session/start` | 201 | [→](#2-post-agentsessionstart) |
| 3 | `GET` | `/agent/session/{sessionId}` | 200 | [→](#3-get-agentsessionsessionid) |
| 4 | `DELETE` | `/agent/session/{sessionId}/end` | 200 | [→](#4-delete-agentsessionsessionidend) |
| 5 | `GET` | `/agent/session/{sessionId}/history` | 200 | [→](#5-get-agentsessionsessionidhistory) |
| 6 | `GET` | `/agent/session/my` | 200 | [→](#6-get-agentsessionmy) |
| 7 | `GET` | `/agent/session/{sessionId}/actions` | 200 | [→](#7-get-agentsessionsessionidactions) |

---

## 1. `POST /agent/chat`

**The main endpoint.** Everything else is supporting cast.

### Request — `ChatRequest`

```json
{
  "sessionId": "3f8a1c22-9d44-4e01-b7aa-5c2f19e8d100",
  "message": "Add a black JBL speaker to my cart"
}
```

| Field | Type | Required | Validation | Notes |
|---|---|---|---|---|
| `sessionId` | UUID | ❌ | — | Omit on first message → session auto-created |
| `message` | String | ✅ | `@NotBlank`, max 1000 chars | Natural language |

### Internal flow

```
AgentController.chat()
  └─> AgentChatService.chat(request)
        │
        ├─ userId = UserContext.getUserId()          ← ThreadLocal
        │
        ├─ STEP 1  resolveSession(userId, sessionId)
        │           ├─ sessionId != null  → Postgres findById + ownership check
        │           ├─ Redis user:session:{uid} hit → Postgres findById
        │           └─ else → startSession() → Postgres findById
        │
        ├─ STEP 2  sessionContextService.findBySessionId(sessionId)
        │           └─ MISS → rebuildContextFromPostgres()   [self-healing]
        │
        ├─ STEP 3  ctx.conversationMessages.add(user msg)
        │           agentSessionService.saveMessage(USER)     → INSERT agent_message
        │
        ├─ STEP 4  ┌───────── AGENTIC LOOP (max 10) ─────────┐
        │          │ callLlmApi(ctx.conversationMessages)      │
        │          │   POST anthropic /v1/messages             │
        │          │   { model, max_tokens, system,            │
        │          │     tools[12], messages[] }               │
        │          │                                           │
        │          │ switch(stop_reason):                      │
        │          │   "end_turn"   → text, save, BREAK        │
        │          │   "tool_use"   → execute, CONTINUE ───────┤
        │          │   "pause_turn" → clarify, BREAK           │
        │          │   other        → error msg, BREAK         │
        │          └───────────────────────────────────────────┘
        │
        ├─ STEP 5  sessionContextService.save(ctx)   → Redis SET x2, TTL 60m
        │
        ├─ STEP 6  CLARIFYING && no pending → status = ACTIVE
        │
        └─ STEP 7  build ChatResponse
```

### Per-tool-call side effects (inside `tool_use` branch)

| Order | Operation | Target |
|---|---|---|
| 1 | `saveMessage(TOOL_CALL)` ⚠️ *stored as `USER` role* | `agent_message` INSERT |
| 2 | `saveAction(type, payload, PENDING)` | `agent_actions` INSERT |
| 3 | `toolExecutionService.executeTool()` | → microservice HTTP |
| 4 | `toolFailed = result.contains("\"error\": true")` | in-memory |
| 5 | `extractResourceId()` | in-memory |
| 6 | `updateActionResult(SUCCESS\|FAILURE)` | `agent_actions` UPDATE |
| 7 | `saveMessage(TOOL_RESULT)` | `agent_message` INSERT |
| 8 | `updateContextFromToolResult()` + append 2 blocks | Redis object (in-memory until Step 5) |

### Response — `ChatResponse`

```json
{
  "success": true,
  "message": "Chat processed",
  "data": {
    "sessionId": "3f8a1c22-9d44-4e01-b7aa-5c2f19e8d100",
    "message": "I've added the JBL Flip 6 (Black) to your cart. Total: ₹8,999.",
    "sessionStatus": "ACTIVE",
    "clarificationNeeded": null,
    "quickReplies": null,
    "actionSummary": {
      "actionType": "ADD_TO_CART",
      "resourceId": null,
      "details": "Item added to your cart"
    },
    "toolCallsMade": [
      { "toolName": "searchProducts", "status": "SUCCESS", "description": "Found products matching your query" },
      { "toolName": "getProductDetails", "status": "SUCCESS", "description": "Retrieved product details and variants" },
      { "toolName": "addToCart", "status": "SUCCESS", "description": "Item added to cart" }
    ]
  }
}
```

| Field | Source | Meaning |
|---|---|---|
| `message` | LLM `content[].text` | What to display in the chat bubble |
| `sessionStatus` | Postgres `AgentSession.status` | `ACTIVE` / `CLARIFYING` / `CLOSED` … |
| `clarificationNeeded` | `ctx.pendingClarificationFor` | `COLOR` / `SIZE` / `ADDRESS` / `CONFIRM_ORDER` / `QUANTITY` / `GENERAL` / `null` |
| `quickReplies` | `buildQuickReplies(topic)` | Suggested buttons; `null` when no clarification |
| `actionSummary` | First SUCCESS in `toolCallsMade` | Highlight banner: `ORDER_PLACED` / `PAYMENT_INITIATED` / `ADD_TO_CART` |
| `toolCallsMade` | Accumulated during loop | Transparency / "agent trace" UI |

### Clarification variant

```json
{
  "sessionId": "3f8a...",
  "message": "Which color would you like — Black or Blue?",
  "sessionStatus": "CLARIFYING",
  "clarificationNeeded": "COLOR",
  "quickReplies": ["Black", "white", "Silver", "Other"],
  "actionSummary": null,
  "toolCallsMade": [
    { "toolName": "getProductDetails", "status": "SUCCESS", "description": "Retrieved product details and variants" }
  ]
}
```

### Degraded variant (circuit breaker open)

```json
{
  "message": "I couldn't add that to your cart right now — the cart service is temporarily unavailable. Shall I try again?",
  "sessionStatus": "ACTIVE",
  "actionSummary": null,
  "toolCallsMade": [
    { "toolName": "addToCart", "status": "FAILED", "description": "Failed: addToCart" }
  ]
}
```

### Fallback messages

| Condition | Response `message` |
|---|---|
| Loop hit 10 iterations | `"I've been thinking too long. Could you please rephrase your request?"` |
| Unknown `stop_reason` | `"I encountered an issue. Could you please try again?"` |
| No text block in content | `"I'm not sure how to respond to that. Could you rephrase?"` |

### Errors

| Cause | Status | Body `message` |
|---|---|---|
| Blank / >1000 char message | 400 | `"message: Message cannot be blank"` |
| Unknown `sessionId` | 404 | `"Session not found: <uuid>"` (`SessionNotFoundException`) |
| `sessionId` owned by another user | 403 | `"You are not authorized to access session: <uuid>"` |
| Redis has sessionId, Postgres doesn't | 500 | `"Session state inconsistency"` |
| Anthropic non-200 | 500 | `"LLM api error: 429"` |
| Malformed LLM JSON | 500 | `"Failed to parse LLM response as json"` |

---

## 2. `POST /agent/session/start`

Optional — `/chat` auto-creates a session. Useful for showing a greeting before the user types.

### Request — `StartSessionRequest` (body optional)

```json
{ "initialIntent": "buy headphones" }
```

### Internal flow

```
AgentSessionService.startSession(request)      @Transactional
  │
  ├─ Redis GET user:session:{userId}
  │    └─ PRESENT → return existing sessionId (idempotent, no writes)
  │
  └─ ABSENT:
       ├─ INSERT agent_session (status=ACTIVE, initial_intent)
       ├─ build SessionContext { sessionId, userId, userEmail,
       │                          currentIntent, messages=[] }
       └─ Redis SET session:{id} + user:session:{uid}, TTL 60m
```

### Response — `StartSessionResponse` (201)

New session:
```json
{
  "success": true,
  "message": "Session started successfully",
  "data": {
    "sessionId": "3f8a1c22-9d44-4e01-b7aa-5c2f19e8d100",
    "status": "ACTIVE",
    "message": "Session started. How can I help you today?",
    "createdAt": "2026-08-29T10:15:30"
  }
}
```

Already had one (idempotent hit):
```json
{
  "data": {
    "sessionId": "3f8a1c22-9d44-4e01-b7aa-5c2f19e8d100",
    "status": "ACTIVE",
    "message": "You already have an active session. Continuing from where you left off.",
    "createdAt": "2026-08-29T10:42:11"
  }
}
```

> ⚠️ On the idempotent path `createdAt` is `LocalDateTime.now()`, **not** the real session creation time.

---

## 3. `GET /agent/session/{sessionId}`

Merges **Postgres (durable)** + **Redis (live)**.

### Internal flow

```
getSessionStatus(sessionId)
  ├─ findAndValidateSession(sessionId, userId)     ← 404 / 403 guard ✅
  ├─ Postgres COUNT agent_message
  ├─ Postgres SELECT agent_actions
  └─ Redis GET session:{id}        (optional enrichment)
       ├─ pendingClarificationFor
       ├─ lastActivityAt
       └─ lastAgentMessage  ← reverse scan for last role=="assistant"
```

### Response — `SessionStatusResponse`

```json
{
  "sessionId": "3f8a1c22-9d44-4e01-b7aa-5c2f19e8d100",
  "userId": 42,
  "status": "CLARIFYING",
  "initialIntent": "buy headphones",
  "totalMessages": 12,
  "totalActions": 4,
  "createdAt": "2026-08-29T10:15:30",
  "updatedAt": "2026-08-29T10:22:47",
  "pendingClarificationFor": "COLOR",
  "lastAgentMessage": "Which color would you like — Black or Blue?",
  "lastActivityAt": "2026-08-29T10:22:47"
}
```

| Field | Source |
|---|---|
| `status`, `initialIntent`, `createdAt`, `updatedAt` | Postgres |
| `totalMessages`, `totalActions` | Postgres COUNT |
| `pendingClarificationFor`, `lastAgentMessage` | Redis — **`null` if TTL expired** |
| `lastActivityAt` | Redis, falls back to `session.updatedAt` |

### Errors

| Cause | Status |
|---|---|
| Session doesn't exist | 404 `SessionNotFoundException` |
| Session belongs to another user | 403 `UnauthorizedSessionAccessException` |

---

## 4. `DELETE /agent/session/{sessionId}/end`

### Internal flow

```
endSession(sessionId)                @Transactional
  ├─ findAndValidateSession()                       ← 404 / 403 guard ✅
  ├─ if status ∈ {ACTIVE, CLARIFYING}
  │     └─ UPDATE agent_session SET status = CLOSED
  └─ Redis DEL session:{id}, DEL user:session:{uid}
```

Postgres rows are **kept forever** (audit); only Redis working memory is purged.

### Response (200)

```json
{ "success": true, "message": "Session ended successfully", "timestamp": "2026-08-29T10:30:00" }
```

---

## 5. `GET /agent/session/{sessionId}/history`

Full transcript from Postgres — **includes tool calls**, unlike the Redis rebuild path.

### Internal flow

```
getConversationHistory(sessionId)
  ├─ findAndValidateSession()                       ← 404 / 403 guard ✅
  ├─ SELECT * FROM agent_message
  │     WHERE session_id = ? ORDER BY sequence_number ASC
  ├─ if empty → throw RuntimeException ⚠️ (becomes generic 500)
  └─ ModelMapper → List<MessageResponse>
```

### Response — `ConversationHistoryResponse`

```json
{
  "sessionId": "3f8a1c22-9d44-4e01-b7aa-5c2f19e8d100",
  "totalMessages": 5,
  "messages": [
    {
      "messageId": "a1...", "role": "USER",
      "content": "Add a black JBL speaker to my cart",
      "toolName": null, "toolInput": null, "toolOutput": null,
      "sequenceNumber": 0, "createdAt": "2026-08-29T10:15:31"
    },
    {
      "messageId": "a2...", "role": "USER",
      "content": null,
      "toolName": "searchProducts",
      "toolInput": "{\"search\":\"JBL speaker\"}",
      "toolOutput": null,
      "sequenceNumber": 1, "createdAt": "2026-08-29T10:15:33"
    },
    {
      "messageId": "a3...", "role": "TOOL_RESULT",
      "content": null,
      "toolName": "searchProducts",
      "toolInput": null,
      "toolOutput": "{\"products\":[{\"productId\":88,\"name\":\"JBL Flip 6\"}]}",
      "sequenceNumber": 2, "createdAt": "2026-08-29T10:15:34"
    },
    {
      "messageId": "a4...", "role": "ASSISTANT",
      "content": "I've added the JBL Flip 6 (Black) to your cart.",
      "sequenceNumber": 3, "createdAt": "2026-08-29T10:15:38"
    }
  ]
}
```

**Reading the roles:**

| Row shape | Actual meaning |
|---|---|
| `role=USER`, `content` set | Real human message |
| `role=USER`, `toolName`+`toolInput` set | ⚠️ Tool **call** — should be `TOOL_CALL` |
| `role=TOOL_RESULT`, `toolOutput` set | Tool response |
| `role=ASSISTANT`, `content` set | Agent reply |

> **Rendering tip:** filter to `role=USER && content != null` plus `role=ASSISTANT` for the chat bubbles; use the tool rows for an expandable "agent trace" panel.

---

## 6. `GET /agent/session/my`

### Internal flow

```
getMySessions()
  ├─ SELECT * FROM agent_session
  │     WHERE user_id = ? ORDER BY created_at DESC
  └─ for EACH session:                       ⚠️ N+1 queries
       ├─ COUNT agent_message
       ├─ SELECT agent_actions
       └─ buildOutcomeString(session, actions)
```

### `buildOutcomeString()` precedence

```
1. any PLACE_ORDER | BUY_NOW  == SUCCESS  → "Order #{resourceId} placed successfully"
                                             (or "Order placed successfully" if no resourceId)
2. any INITIATE_PAYMENT       == SUCCESS  → "Payment initiated - awaiting completion"
3. fallback on session.status:
      COMPLETED  → "Completed Successfully"
      FAILED     → "Session failed - please try again"
      CLOSED     → "Session closed without completing"
      CLARIFYING → "Waiting for your response"
      ACTIVE     → "In progress"
```

### Response — `MySessionResponse`

```json
{
  "totalSessions": 2,
  "sessions": [
    {
      "sessionId": "3f8a...", "status": "ACTIVE",
      "initialIntent": "buy headphones",
      "outcome": "Order #1042 placed successfully",
      "totalMessages": 14, "totalActions": 6,
      "createdAt": "2026-08-29T10:15:30", "updatedAt": "2026-08-29T10:28:12"
    },
    {
      "sessionId": "7b2c...", "status": "CLOSED",
      "initialIntent": null,
      "outcome": "Session closed without completing",
      "totalMessages": 3, "totalActions": 1,
      "createdAt": "2026-08-28T18:02:10", "updatedAt": "2026-08-28T18:05:44"
    }
  ]
}
```

> No ownership check needed — the query itself is scoped by `userId`.

---

## 7. `GET /agent/session/{sessionId}/actions`

The **business audit trail** — what the agent actually *did* (vs what it *said*).

### Internal flow

```
getSessionActions(sessionId)
  ├─ findAndValidateSession()                       ← 404 / 403 guard ✅
  ├─ SELECT * FROM agent_actions
  │     WHERE session_id = ? ORDER BY created_at ASC
  └─ ModelMapper → List<ActionResponse>
```

### Response — `SessionActionResponse`

```json
{
  "sessionId": "3f8a1c22-9d44-4e01-b7aa-5c2f19e8d100",
  "totalActions": 3,
  "actions": [
    {
      "actionId": "b1...",
      "actionType": "SEARCH_PRODUCT",
      "status": "SUCCESS",
      "requestPayload": "{\"search\":\"JBL speaker\"}",
      "responsePayload": "{\"products\":[...]}",
      "resourceId": null,
      "failureReason": null,
      "createdAt": "2026-08-29T10:15:33",
      "updatedAt": "2026-08-29T10:15:34"
    },
    {
      "actionId": "b2...",
      "actionType": "BUY_NOW",
      "status": "SUCCESS",
      "requestPayload": "{\"productId\":88,\"variantId\":301,\"quantity\":1,\"addressId\":7}",
      "responsePayload": "{\"orderId\":1042,\"totalAmount\":8999}",
      "resourceId": "1042",
      "failureReason": null,
      "createdAt": "2026-08-29T10:22:01",
      "updatedAt": "2026-08-29T10:22:03"
    },
    {
      "actionId": "b3...",
      "actionType": "INITIATE_PAYMENT",
      "status": "FAILURE",
      "requestPayload": "{\"orderId\":1042,\"amount\":8999}",
      "responsePayload": "{\"error\": true, \"tool\": \"initiatePayment\", \"message\": \"Payment service is temporarily unavailable...\"}",
      "resourceId": null,
      "failureReason": "{\"error\": true, \"tool\": \"initiatePayment\", \"message\": \"Payment service is temporarily unavailable...\"}",
      "createdAt": "2026-08-29T10:22:10",
      "updatedAt": "2026-08-29T10:22:14"
    }
  ]
}
```

### `resourceId` extraction rules

| `actionType` | Parsed from tool result |
|---|---|
| `PLACE_ORDER`, `BUY_NOW` | `orderId` |
| `ADD_TO_CART` | `cartItemId` |
| `INITIATE_PAYMENT` | `paymentId` |
| everything else | `null` |

### Status meanings

| Status | When |
|---|---|
| `PENDING` | Row inserted, tool not finished — **stuck here means the JVM died mid-call** |
| `SUCCESS` | Result did not contain `"error": true` |
| `FAILURE` | Result contained `"error": true` (exception, unknown tool, or circuit-breaker fallback) |
| `SKIPPED` | Declared in the enum, never set |

---

## Tool → Microservice Quick Table

What each LLM tool ultimately hits:

| Tool | HTTP call | Service |
|---|---|---|
| `searchProducts` | `GET /products/all?search=&category=&page=0&size=10` | product |
| `getProductDetails` | `GET /products/details/{productId}` | product |
| `getVariantInfo` | `GET /products/{pid}/variants/{vid}/item-info` | product |
| `getCart` | `GET /cart` | cart |
| `addToCart` | `POST /cart/items` | cart |
| `clearCart` | `DELETE /cart/clear` | cart |
| `placeOrder` | `POST /orders` | order |
| `buyNow` | `POST /orders/buy-now` | order |
| `getOrder` | `GET /orders/{orderId}` | order |
| `getMyOrders` | `GET /orders/my-orders` | order |
| `initiatePayment` | `POST /payments/initiate` | payment |
| `getAllAddresses` | `GET /users/address/all` | user |

Every one is wrapped in `@Retry(3, 500ms)` + `@CircuitBreaker(50% / 10 calls / 30s)` with a user-friendly fallback.

---

## Error Response Catalog

| Exception | Status | Example body |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `{"success":false,"message":"message: Message cannot be blank"}` |
| `BadRequestException` | 400 | `{"success":false,"message":"<reason>"}` |
| `SessionNotFoundException` | 404 | `{"success":false,"message":"Session not found: <uuid>"}` |
| `UnauthorizedSessionAccessException` | 403 | `{"success":false,"message":"<uuid>"}` |
| `AgentException` | 500 | `{"success":false,"message":"LLM api error: 429"}` |
| `ToolCallException` | 502 | `{"success":false,"message":"Service call failed: ..."}` |
| `Exception` (catch-all) | 500 | `{"success":false,"message":"Something went wrong, Please try again."}` |

---

## Endpoint Security Matrix

| Endpoint | Ownership validated? |
|---|---|
| `POST /chat` | ✅ `resolveSession()` verifies `session.userId == caller` |
| `POST /session/start` | ✅ N/A — scoped by `userId` |
| `GET /session/{id}` | ✅ `findAndValidateSession()` |
| `DELETE /session/{id}/end` | ✅ `findAndValidateSession()` |
| `GET /session/{id}/history` | ✅ `findAndValidateSession()` |
| `GET /session/my` | ✅ query scoped by `userId` |
| `GET /session/{id}/actions` | ✅ `findAndValidateSession()` |

---

## Typical Frontend Call Sequence

```
1. App opens
   POST /agent/session/start          → sessionId + greeting

2. Every user message
   POST /agent/chat { sessionId, message }
     ├─ render data.message                  as agent bubble
     ├─ render data.toolCallsMade            as collapsible trace
     ├─ render data.quickReplies             as buttons (if present)
     └─ render data.actionSummary            as highlight banner (if present)

3. Reconnect / page refresh
   GET /agent/session/{id}/history     → rebuild the chat UI
   GET /agent/session/{id}             → is it still CLARIFYING?

4. "My conversations" screen
   GET /agent/session/my               → list with outcome strings

5. Order/debug view
   GET /agent/session/{id}/actions     → audit trail

6. User clicks "New chat"
   DELETE /agent/session/{id}/end
   POST   /agent/session/start
```





