# Calyvora

The **AI-Native Enterprise Operating System** — one platform, one identity, one data fabric, one AI
layer. This repository is a monorepo; the binding architecture lives in [`/docs`](docs/README.md) and
the narrative decision log in [`FOUNDER.md`](FOUNDER.md).

The product is branded **Orbit** (Calyvora is the parent company).

The **Phase-1 trio is complete**: the Platform Foundation (Sprint 1) plus three full apps on it —
**People OS**, **Work OS**, and **Knowledge OS** — all verified live, integrated by construction
(a Knowledge page's author is a People employee and links a Work task). Since then, founder feedback has
added **Clients** and **Documents** (template-driven joining/relieving/offer letters that fill themselves
in from People profiles). Sprint plans:
[Sprint 1](docs/Sprint1.md) · [People OS](docs/Sprint2-PeopleOS.md) · [Work OS](docs/Sprint3-WorkOS.md) ·
[Knowledge OS](docs/Sprint4-KnowledgeOS.md) · [Founder feedback backlog](docs/Founder-Feedback-Backlog.md).
Practical build/run state: [`CONTEXT.md`](CONTEXT.md).

## Layout

| Path | What |
|------|------|
| `backend/` | Spring Boot 3 · Java 21 · **Maven**. Feature-organized packages under `com.calyvora`. |
| `frontend/` | Next.js (App Router) · TypeScript · Tailwind. The product web app. |
| `infra/` | `docker-compose.yml` — local Postgres + Mailpit. |
| `web/` | Standalone static marketing site (deploy target for calyvora.in) — separate from the app. |
| `docs/` | Architecture constitution (00–15) + sprint plans. |

## Prerequisites

- **Node 20+** (frontend) — installed.
- **JDK 21** (Temurin) + **Maven 3.9+** (backend).
- **Docker Desktop** (local Postgres + Mailpit, and the backend's Testcontainers integration tests).

## Run locally

```bash
# 1. Infra (Postgres :5432, Mailpit SMTP :1025 / UI :8025)
docker compose -f infra/docker-compose.yml up -d

# 2. Backend (http://localhost:8080, Swagger at /swagger-ui.html)
cd backend && mvn spring-boot:run

# 3. Frontend (http://localhost:3000)
cd frontend && npm install && npm run dev
```

The frontend proxies `/api/*` to the backend (see `frontend/next.config.mjs`), so the browser talks
same-origin in dev and the refresh-token cookie stays first-party.

## Tests

```bash
cd backend && mvn verify        # unit + Testcontainers integration (needs Docker)
cd frontend && npm run test     # Vitest component tests
cd frontend && npm run e2e      # Playwright happy-path E2E
```

CI (`.github/workflows/ci.yml`) runs both stacks on every push/PR.
