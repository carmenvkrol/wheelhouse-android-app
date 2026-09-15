# Android App — Requirements

**Status: DRAFT v0.3 — 2026-07-27 — pending Patrick's pass; not yet handed to Carmen.**
Sections marked **[PATRICK]** need his numbers or words. Everything else is his to veto or edit.

This is a requirements document, not a design document. It says *what* the app must do and *why*; every *how* — architecture, stack, UI design, libraries — belongs to the builder. When a how-question arises, this doc answers with constraints, and the question plus its answer land in §11 so the doc accumulates instead of repeating. System context: `../ARCHITECTURE.md`. Terms: `../GLOSSARY.md`.

---

> **Note:** section 1 ("Why this exists") is omitted from this public copy.
> Numbering is unchanged, so section references elsewhere still resolve.

## 2. Success criteria — the bar

Set by Patrick 2026-07-27. The instrument succeeds if it produces a clear answer, including a clear *no*. All measured at the **floor** fill assumption, net, on total leveraged capital:

- **Return floor:** ≥ **10%/yr** *and* beats **JEPQ total return** over the same window (the real-world do-nothing alternative). Target band 10–20%. Benchmark set: QQQ TR · cash hurdle · index put-write · JEPQ.
- **Forward test:** ≥ **1 month**, a full quarter to be satisfying. Backtests carry the primary statistical weight; the forward book corroborates machinery, fills, and the current regime.
- **Drawdown:** peak-to-trough > **20% of principal** in any hostile-window replay (2022, Q4 2018, Mar 2020) = failure regardless of returns. Patrick flagged a lean toward tighter — any tightening must land **before** the first hostile replay runs (pre-commitment integrity).
- **Automatable — a trend, not a threshold:** the veto rate **declines across rubric versions** and settles low, with loss-dodging vetoes trending to zero. A static rubric persistently overridden = failure. (Learning loop: vetoes + reasons → proposed rubric diffs → Patrick-approved version bumps; every result stamped with its rubric version.)
- **Distrust rules for a passing result:** P&L attribution must show wins coming from the claimed edge — **theta + vega, not delta** ("if I'm getting a lot of wins on a ticker via delta and not theta, that is actually a red flag"). Standard secondary checks: profit concentrated in one name or one month · floor-to-mid band too wide (fill-luck dependence) · put-write index matching the result (selection added nothing) · gains confined to a single regime.

## 3. Users

- **v1: Patrick, single login** — operator: daily glances, approvals/vetoes, weekly review.
- **Carmen** — builder. Not a v1 user role.
- **Future trajectory (explicit, 2026-07-27):** designed single-user, envisioned multi-account — candidate future users include family members running it on their own accounts (each with their own rubric and book) and at least one acquaintance who has expressed willingness to pay for exactly this. Hence the day-one rule: every API datum is account-scoped, so multi-tenancy later is authz work, not a rebuild.

## 4. System boundary

The app talks only to the API (`../api/CONTRACT.md`) served from the cloud edge, and receives FCM pushes. Engine internals (hosting, LLM, data vendors) are invisible to the app by design.

**The app must be fully buildable and demoable against `../api/fixtures/`** — every screen state reachable with the backend switched off. If the app ever blocks on the engine, that's a bug in Patrick's deliverable, not Carmen's.

## 5. Domain knowledge

`../GLOSSARY.md` — the wheel in four sentences plus every term the API uses. Questions beyond it go to §11 and then into the glossary.

## 6. Functional requirements

### 6.1 Positions board — "what's going on"
- One entry per underlying: wheel state, open legs, DTE, **time to auto-exit** (day-14 stop or expiry, whichever fires first), strike vs. current price (distance %), premium captured vs. remaining, delta, unrealized P&L (band-labeled), margin consumed.
- **Per-position IV context:** IV at entry → IV now, and the mark-P&L attribution split (delta vs. theta+vega) — a red position wearing a transient vega mark must look visibly different from one sliding toward its strike.
- Aggregates: margin utilization, net exposure, synthetic cash. If twin books are live, the board makes the active book unambiguous.
- Every screen shows data age (§7.1).

### 6.2 Home dashboard — the launch surface (reframed by Patrick, 2026-07-27)
- The first screen answers four questions, in this order:
  1. **Any decisions needed right now?** Pending cards, deadline-sorted — they dominate the surface whenever present.
  2. **How much have we made?** Realized + unrealized P&L for today / this week / this month / this year, band-labeled.
  3. **How leveraged are we?** Deployed vs. idle against the configured target capital (idle capital is why per-position APR ≠ portfolio return — the success bar is judged on the total).
  4. **How are positions doing?** Per-position quick status: profit/loss and **time until the engine auto-exits** (day-14 stop or expiry, whichever comes first).
- PR #1's empty-state already carries most of 2–4; this promotes that content to the home surface's definition in *both* states. Whether a separate full-height Decisions tab also exists is the builder's call.
- One card per proposal: action, the reasons payload rendered legibly, and the **deadline**.
- **Approve / Veto / Snooze.** Veto requires a one-line reason (free text — this is rubric-training data). Approvals require a live round-trip; no offline queuing of approvals (deadlines can pass).
- Stop/time-trigger cards carry the attribution of the adverse move (delta-driven vs. vega-driven) — the hold-vs-exit judgment depends on it (`../backend/RUBRIC.md`, vega exception).
- Pending count exposed for badge/widget use.

### 6.3 Backtest browser
- Run list with parameters and headline results; per-run equity curve vs. the three benchmarks, drawdown curve, trade list, per-trade drill-down (entry/exit, fill band, the rule that fired); compare any two runs.
- Charts stay smooth at a few thousand points on a mid-range phone.

### 6.4 Risk screen
- The **if-everything-assigns** number vs. available synthetic cash+margin; concentration by underlying/sector; largest single-name exposure.
- **Kill switch**: pause new entries (engine keeps managing exits). One tap, reversible, round-trips through the API.

### 6.5 Journal
- Chronological feed of every engine action with its stated reasoning, filterable. This is the trust and audit surface.

### 6.6 Notifications
- Three classes (hard requirement): **FYI** silent digest · **DECISION** actionable (approve/veto from the notification where the platform allows — approve still round-trips — deadline shown, deep link to the card) · **ALARM** loud (gap through strike, margin threshold, engine/data down, staleness).
- Target: ~90% of pushes are FYI-class. The app succeeds when it earns *not being checked*.
- **Notification triage (Patrick, 2026-07-27):** deferrable decisions **respect the device's own notification/DND settings**; emergencies **interrupt like a pager**. FYI never interrupts. ALARM always does (DND-bypass channel, permission granted by the user). DECISION urgency is triaged by the engine — deferrable by default, escalated to pager-class as its deadline nears: a decision suppressed by quiet settings must never expire unseen, and pre-expiry escalation is the engine's job (push payload carries `urgency`, see CONTRACT).
- Home-screen widget (positions summary + pending-decision count): **in v1** (Patrick: "sounds fun").

### 6.7 The band — honesty requirement
- Every displayed P&L labels which band value it shows ({floor, policy, mid} per the contract). Floor is the default where space is tight.

### 6.8 Settings
- A settings surface exists (gap caught in PR #1): per-class notification preferences, band display default, device/account management, and book selection if twin books ship. Contents grow with the app; presence is the requirement.

## 7. Non-functional requirements

1. **Freshness always visible** — last-sync on every screen; stale banner past the engine-provided threshold. A monitoring app silently showing old positions is worse than no app.
2. **Offline** — read-only last-synced snapshot, clearly marked stale; no offline approvals.
3. **Privacy** — household financial data: no third-party analytics/crash SDKs that ship data off-device without prior discussion; TLS; token auth; no financial data in URLs.
4. **Platform** — Android first; minSdk and device targets are the builder's call. iOS is a later maybe — don't pay abstraction costs for it in v1 beyond what comes free.
5. **Performance** — cold start to the home surface ~2s on target hardware; chart interactions smooth. *(Was "positions board," contradicting §6.2's home = decision queue — inconsistency caught by Carmen's design reading, PR #1.)*

## 8. API

`../api/CONTRACT.md` is the single source of truth (endpoints, card shape, push payloads, band rule, versioning). Fixtures: `../api/fixtures/`. Contract questions land in the contract's changelog, not here.

## 9. Strategy rubric + decision inventory

- **Rubric and sizing: `../backend/RUBRIC.md`** (v0.1, elicited 2026-07-27) — universe tiers, the APR entry gate, strike rule, the exit trio, earnings book, sizing structure. It defines what the §6.2 cards say.
- **Decision inventory:** the gate matrix in `../backend/RUBRIC.md` — confirmed 2026-07-27. Defines which §6.2 card types exist (entries, vega-driven triggers, post-loss branches, post-assignment choices) vs. what merely journals as FYI.

## 10. Non-goals (v1)

No live orders. No broker credentials. No multi-user UI. No iOS. No social. No in-app strategy editing (the app observes and approves; the rubric lives engine-side). No general market-data browsing — the app shows the system's own state, not a quote terminal.

## 11. Open questions & changelog

| Date | Q / change | Answer / status |
|---|---|---|
| 2026-07-27 | Is Carmen also an observer user (own token, read-only)? | resolved: v1 single-user (Patrick); future = full multi-account trajectory (§3), observer role subsumed |
| 2026-07-27 | Home-screen widget in v1? | resolved: **in v1** |
| 2026-07-27 | Quiet hours / DND policy for DECISION pushes? | resolved: **triage model** (§6.6) — deferrable respects device settings, emergencies page |
| 2026-07-27 | **Twin books (BASE+TASTE) in v1?** — Carmen's top blocking UI question (PR #1 §7) | **resolved (Patrick): BASE-only v1** — no book switcher, no book-scoping in v1; both arrive with TASTE at v1.1 (the wireframes' assumption is now official) |
| 2026-07-27 | Deep-link scheme name | builder's call; record in CONTRACT.md once chosen |
| 2026-07-27 | v0.8: §6.2 reframed as the **home dashboard** (Patrick) — four ordered questions: decisions? · windowed P&L (new requirement — existed nowhere) · leverage vs. target capital · positions w/ **time-to-auto-exit** (new); §6.1 rows gain the auto-exit clock; contract gains `/v1/pnl` + `next_auto_exit` | — |
| 2026-07-27 | v0.7: widget → **v1**; quiet-hours → **triage model**; §7.5 home-surface inconsistency fixed + §6.8 settings surface added (both caught by PR #1); twin-books question promoted to top open item | — |
| 2026-07-27 | v0.6: user model set (v1 Patrick-only, multi-account trajectory named). All [PATRICK] blanks resolved; doc is now live — Carmen is building against it (PR #1 landed before this row was written) | — |
| 2026-07-27 | v0.5: vega exception surfaced in UI reqs — positions show entry→now IV + attribution split; trigger cards carry delta/vega attribution | — |
| 2026-07-27 | v0.4: §2 success bar filled (return floor 10% + JEPQ-relative · 1mo–1qtr forward · 20% drawdown fail line · veto-trend redefinition · attribution distrust rule) | — |
| 2026-07-27 | v0.3: §9 rubric+sizing → `backend/RUBRIC.md` (elicitation complete); decision inventory still pending | — |
| 2026-07-27 | v0.2: moved into WheelHouse monorepo; §5→glossary pointer, §8→contract pointer; account-scoping noted | — |
| 2026-07-27 | v0.1 drafted (in jarvis workspace) | — |

**Working protocol:** questions land here with a date; answers fold into the relevant section; the changelog row records both. The design doc, milestone plan, and all implementation choices are the builder's own documents (this repo hosts them when she wants — `android/` is hers).
