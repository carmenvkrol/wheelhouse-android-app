# WheelHouse mock API server

A local HTTP server implementing the `api/CONTRACT.md` endpoints the currently-built
app screens need (Android's Home dashboard, Positions board + detail, and Risk
screens). It exists so Android, iOS, and web clients can all point at the same running
process during local dev — one server, N clients — instead of each app carrying its own
private fixture data.

**This is not the "fixtures pack"** `api/fixtures/README.md` describes. That pack is
backend-generated static JSON, owned by Patrick, and still spec-only. This is a
hand-built dev tool that serves contract-shaped JSON dynamically over HTTP, explicitly
scoped to local development — a stand-in for the real API until it exists, not a
replacement for the real fixtures pack once it does.

## Running it

No dependencies beyond a JDK — it's plain Java using the JDK's built-in
`com.sun.net.httpserver.HttpServer`, chosen because this machine's default `java` on
`PATH` turned out to be Java 7 (and Node/Python here were both unusably outdated too).
If your `java`/`javac` are modern (17+), `./run.sh` just works. Otherwise point
`JAVA_HOME` at a working JDK — e.g. the one Android Studio ships:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./run.sh
```

Listens on `http://127.0.0.1:8787` by default (pass a port as the first arg to change
it). State (pending decisions, the pause flag) is in-memory and resets on restart.

## Reaching it from each client

- **Web**, same machine: `http://localhost:8787` directly. CORS is wide open
  (`Access-Control-Allow-Origin: *`) so any dev-server origin can call it.
- **iOS Simulator**: shares the host's network — `http://localhost:8787` works
  unchanged. A physical iPhone needs the Mac's LAN IP instead (same Wi-Fi).
- **Android emulator**: `http://10.0.2.2:8787` (the emulator's alias for the host).
- **Android physical device over USB**: `adb reverse tcp:8787 tcp:8787`, then
  `http://127.0.0.1:8787` from the device resolves to the Mac. (This is what the
  Android app in this repo uses — see `ApiConfig.kt`.)

## Endpoints

Implements: `GET /v1/status`, `/v1/positions`, `/v1/pnl`, `/v1/decisions`, `/v1/risk`;
`POST /v1/decisions/{id}`, `/v1/pause`, `/v1/resume`, `/v1/device`. Shapes follow
`api/CONTRACT.md`; where the contract only sketches a shape in prose (positions, pnl,
risk, status — it's marked "v0 sketch" pending a full schema pass), the JSON here is the
concrete elaboration the built Android screens already need, not a new contract
decision.

`GET /v1/journal` and `/v1/backtests*` are **not implemented** — no built screen uses
them yet (Journal and Backtests were dropped from the Android nav for the same reason:
no wireframe, no screen). Add them here when a screen needs them.

## Known simplifications

- **Auth is a no-op.** CONTRACT.md's bearer token isn't checked — any `Authorization`
  header (or none) is accepted. Fine for a local mock; would need real enforcement
  before this touched anything but localhost.
- **Decision-relative fields are pre-computed.** `deadline`/`exit.at` carry an ISO-8601
  timestamp per CONTRACT.md, but each decision/position also carries a
  `deadline_label`/`exit.days` convenience field — what the Android client actually
  renders today, since it has no relative-time formatting logic yet. Both are served so
  neither client code nor the mock has to invent the other.
- **Resolving a decision doesn't ripple into positions/pnl/risk.** Approving the TSLA
  entry, for instance, doesn't create a new position or move the aggregates — that's
  real engine logic, out of scope for a client-facing mock. The decision itself does
  correctly disappear from `/v1/decisions` once resolved, and `requires_reason`
  validation is enforced (a 400 if a reason-requiring option is posted without one).
- **Only MSFT carries a `detail` object** in `/v1/positions` — the one ticker with a
  built detail screen. CONTRACT.md doesn't define a separate per-position detail
  endpoint, so the detail fields ride along on the board response instead of inventing
  one.
