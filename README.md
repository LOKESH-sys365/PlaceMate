# PlaceMate

A placement-prep assistant for tracking job applications, DSA practice and deadlines by talking to a
local LLM. Two ideas make it more than a chatbot:

1. **RAG-based tool selection.** Each tool's description is embedded in pgvector. For every message, only
   the top-K most relevant tools are sent to the model instead of all of them.
2. **Human-in-the-loop writes.** Tools that change data are never executed on the model's say-so. The chat
   loop stops, the UI shows exactly what would change, and nothing is saved until the user clicks Apply.

Everything runs locally (Ollama + Postgres). No API keys.

## Stack

Java 21, Spring Boot 3.4, Spring AI 1.0 (Ollama), PostgreSQL + pgvector, Spring Data JPA, plain HTML/CSS/JS frontend.

## How a request flows

```
message ──► ToolSelector ──► vector search over tool descriptions (pgvector, top-K)
                │
                ▼
        ChatService loop (tool execution done by us, not by Spring AI)
                │
     model asks for tools ──► only read tools?  run them, feed results back, continue
                │
                └──► any write tool?  save the proposed calls as a PendingAction and stop
                                          │
                       UI shows a slip ◄──┘
                       Apply  ──► run the tools, continue the loop, model writes the reply
                       Discard ─► tell the model it was declined, nothing is saved
```

Key files:

| File | What it does |
|---|---|
| `tools/PlacementTools.java` | The 8 tools. Write tools carry `@RequiresConfirmation`. |
| `tools/ToolRegistry.java` | Builds callbacks, knows which tools need approval, estimates tool-definition size. |
| `rag/ToolIndexer.java` | Embeds tool descriptions into pgvector at startup (idempotent ids). |
| `rag/ToolSelector.java` | Top-K similarity search plus companion rule (status updates also get the listing tool). |
| `chat/ChatService.java` | Manual tool-calling loop, pending actions, approve/discard. |
| `static/index.html` | Desk view (applications, practice, reminders) and the assistant with approval slips. |

## Run it

Prerequisites: Java 21, Maven, Docker, [Ollama](https://ollama.com).

```bash
docker compose up -d                 # Postgres with pgvector
ollama pull llama3.1:8b              # chat model (supports tool calling)
ollama pull nomic-embed-text         # embeddings, 768 dimensions
mvn spring-boot:run
```

Open http://localhost:8080.

Run the tests with `mvn test` (no Ollama or database needed; they use mocks).

## Measuring the token saving

`GET /api/tools/preview?q=I solved two sum` returns how many tools would be sent and the approximate size of
their definitions versus sending all of them. Try a few messages, write down the real numbers, and use those
on your resume rather than anything estimated in advance.

## Known limitations 

- Conversation history and pending actions are held in memory and expire after 30 minutes. A restart loses them.
  Next step: persist them in Postgres.
- There is no authentication; it is a single-user local app.
- Tool selection quality depends on tool descriptions and the embedding model. The `COMPANION_TOOLS` rule
  exists because a status update needs an application id, which needs the listing tool.
- Small local models sometimes pass wrong argument types. Tool errors are returned to the model so it can retry.
- The approximate token count is characters divided by 4, not a real tokenizer.

## Ideas to extend

- Persist conversations and pending actions in Postgres.
- Add a tool that reads a job posting URL and extracts company, role and deadline.
- Add an evaluation script: a set of labelled messages and the tools each should select, reporting hit rate for different K.
- Add Spring Security and per-user data.
