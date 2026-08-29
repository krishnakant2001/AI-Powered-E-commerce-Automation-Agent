# Agent Service — Documentation

Everything you need to understand `agent-service`, the AI orchestration brain of the platform.

| Document | Read it when you want to… |
|---|---|
| **[ARCHITECTURE.md](./ARCHITECTURE.md)** | Understand *how the service works internally* — layers, storage model, the agentic loop, resilience, and the known-issues backlog |
| **[API-REFERENCE.md](./API-REFERENCE.md)** | Understand *what each endpoint does* — request/response shapes, internal flow per endpoint, error catalog |

---

## The 60-Second Mental Model

If you remember only one thing, remember this:

```
                    ┌──────────────────────────────────┐
                    │       AgentChatService           │
                    │      (the agentic loop)          │
                    └──────────────────────────────────┘
                       ▲            │            │
        conversation   │            │ "what      │ execute
        history        │            │  next?"    │ decision
                       │            ▼            ▼
            ┌──────────┴───┐  ┌──────────┐  ┌──────────────┐
            │ Redis        │  │  Claude  │  │ ToolExecutor │
            │ working mem  │  │   LLM    │  │ → 5 services │
            └──────────────┘  └──────────┘  └──────────────┘
                       │
                       │ mirrored for audit
                       ▼
            ┌──────────────────┐
            │ Postgres         │
            │ permanent record │
            └──────────────────┘
```

**The loop, in one sentence:**
> Load the conversation from Redis → ask Claude what to do → if Claude wants a tool, call the microservice and feed the result back → repeat (max 10×) until Claude produces a final answer → save everything.

---

## The Two Storage Systems (the #1 source of confusion)

| | Redis | Postgres |
|---|---|---|
| Holds | `SessionContext` | `AgentSession`, `AgentMessage`, `AgentAction` |
| Job | The LLM's **working memory** | The **audit log** |
| TTL | 60 minutes | forever |
| Keys | `session:{sessionId}`<br/>`user:session:{userId}` | `session_id` UUID PK |

They store **different Java types**. Redis never holds an `AgentSession` — that is why `resolveSession()` always ends in a Postgres lookup, and only consults Redis to *discover* which sessionId a user is currently on.

If Redis expires, `rebuildContextFromPostgres()` reconstructs the conversation from `agent_message` rows. Nothing is lost.

---

## The Three Ways the Loop Can End

| Claude's `stop_reason` | What the service does |
|---|---|
| `end_turn` | Final answer → save, clear clarification flag, **break** |
| `tool_use` | Run the tool(s), append results to history, **continue** |
| `pause_turn` | Treat as "needs clarification" → status `CLARIFYING`, **break** |
| *(anything else)* | Log a warning, return a generic error, **break** |
| *(10 iterations reached)* | `"I've been thinking too long…"` |

---

## Reading Order for a New Developer

1. **ARCHITECTURE §1–3** — what the service does + package layers
2. **ARCHITECTURE §4** — the dual-storage model *(don't skip this)*
3. **ARCHITECTURE §7–8** — the `/chat` flow and the agentic loop
4. **API-REFERENCE §1** — the `POST /agent/chat` contract
5. **ARCHITECTURE §9** — how a tool name becomes an HTTP call
6. **ARCHITECTURE §15** — the known bugs before you touch anything

---

## Key Files by Importance

| Rank | File | Lines | Why it matters |
|---|---|---|---|
| 1 | `service/AgentChatService.java` | ~560 | The entire agentic loop lives here |
| 2 | `llm/ToolExecutor.java` | ~228 | All downstream calls + resilience fallbacks |
| 3 | `service/AgentSessionService.java` | ~369 | Session CRUD + all Postgres writes |
| 4 | `llm/ToolDefinitionBuilder.java` | ~137 | The 12 tool schemas Claude sees |
| 5 | `service/SessionContextService.java` | ~128 | All Redis reads/writes |
| 6 | `llm/ToolExecutionService.java` | ~72 | toolName → method router |
| 7 | `llm/SystemPromptBuilder.java` | ~28 | The agent's behavior rules |

---

## Quick Facts

- **Port:** `9060` · **DB:** `agentdb` · **Model:** `claude-sonnet-4-5` · **Max tokens:** `1000`
- **Loop cap:** 10 tool iterations per turn
- **Session TTL:** 60 minutes of inactivity
- **Retry:** 3 attempts, 500 ms apart · **Circuit breaker:** opens at 50% failures over 10 calls, recovers after 30 s
- **12 tools** exposed to the LLM across **5 microservices**
- **Auth:** no JWT parsing — gateway injects `X-user-*` headers, relayed onward by `FeignClientInterceptor`

---

## ✅ Critical Bugs — Fixed

1. **`getVariantInfo` was unreachable** — the schema advertised `getVariantInfo` while the switch matched `getProductItemDetails`, so every stock check returned *"Unknown tool"*. The case now matches both names.
2. **`resolveSession()` had no ownership check** — any user could pass any `sessionId` and take over that session. It now verifies the caller owns the session and returns a proper 404/403.
3. **`buildToolUseContent()` swapped its arguments** — `id` received the tool name and `name` received the id; the `input` placeholder was also wrongly quoted. Both corrected.

Remaining (non-critical) items with severity ratings: [ARCHITECTURE §15](./ARCHITECTURE.md#15-known-issues--improvement-backlog).

**Next up (high severity, still open):** `TOOL_CALL` messages saved with role `USER` · `sequenceNumber` derived from `COUNT(*)` (race condition) · `UserContext.clear()` leaking `userRole`/`userEmail` across pooled threads · tool blocks sent as JSON strings instead of Anthropic content blocks.


