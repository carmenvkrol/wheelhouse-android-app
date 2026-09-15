# Android — user flows (as built)

**Purpose:** enumerate the workflows a user can actually walk through the app *today*, so an
accessibility review has a defined set of paths to audit rather than a pile of screens.

**Scope date:** 2026-08-28. Derived from the code, not the wireframes or REQUIREMENTS — a flow
appears here only if it can be walked in the running app.

**Built surfaces (17 flows):** Home dashboard · Positions board · Position detail · Risk.
**Not built** (so: no flows, nothing to review yet): Journal (§6.5), Backtest browser (§6.3),
Settings (§6.8), notifications and deep links (§6.6), home-screen widget, sign-in/auth.

---

## 0. Global chrome — present in every flow below

Every screen renders, top to bottom:

| Element | Where | Notes |
|---|---|---|
| Screen title | app bar | "WheelHouse" / "Positions" / *ticker* / "Risk" |
| Band chip `P&L: FLOOR` | app bar, right | Home, Positions, Detail. **Risk has no chip.** |
| Paused banner | chrome strip | Only when the kill switch is engaged; global, not Risk-only |
| Freshness line `Synced 42s ago · BASE book` | chrome strip | Every screen |
| Bottom nav — Home · Positions · Risk | bottom | 3 slots; Home slot carries a pending-count badge |

Because these repeat, an issue found in the chrome is an issue in *every* flow — worth auditing
once, separately, then treating as a constant.

---

## 1. Global flows

### G1 — Launch and orient (cold start)
- **Entry:** launcher icon.
- **Steps:** app opens on Home, already populated from local fixtures → a single background
  fetch replaces it with server data (status, P&L, positions, decisions, risk) → user reads
  the four dashboard questions in order.
- **Branches:** server unreachable → fixtures stay on screen, freshness line keeps reading
  `42s`, and **nothing on screen says the fetch failed** (it is logged only).
- **End:** user has an answer to "does anything need me?" or leaves the app.
- **Under review:** reading order of the four numbered question sections; whether the number
  badges (1–4) are announced; the all-caps section titles.

### G2 — Switch section
- **Entry:** any screen.
- **Steps:** tap a bottom-nav slot → that section renders, slot marks itself selected
  (top rule + filled glyph + semibold label).
- **Branches:** tapping **Positions** while in Position detail returns to the board, not the
  detail screen.
- **Under review:** the nav glyphs are deliberately empty bordered squares (no icons, per
  design/ui-from-requirements.md §2) — labels carry the entire meaning; selected state is
  conveyed by weight and fill; slots have no `Role`/`selected` semantics set.

### G3 — Change the P&L band
- **Entry:** Home, Positions, or Position detail.
- **Steps:** tap the `P&L: FLOOR` chip → cycles FLOOR → POLICY → MID → FLOOR. Every money
  figure on that screen re-renders, and the trailing band labels update with it.
- **Branches:** band is **per-screen state** — switching sections and coming back resets it to
  FLOOR. Risk shows money with no chip to relabel it.
- **Under review:** a cycling control with no visible list of states; the change is announced
  nowhere; the only feedback is the numbers themselves silently changing.

### G4 — Check freshness / notice the engine is paused
- **Entry:** any screen.
- **Steps:** read the chrome strip; if paused, a dot + "New entries paused. The engine is still
  managing exits." sits above the freshness line.
- **Note:** there is no manual refresh, pull-to-refresh, or polling — the data age shown is
  whatever the single startup fetch returned and it does not tick.

---

## 2. Home / decision flows

Home has two shapes. **Quiet:** question 1 collapses to a one-line strip and all four positions
show. **Busy:** decision cards dominate question 1 and the position list truncates to three.

### H1 — Quiet-book glance
- **Entry:** launch, or nav to Home, with no pending decisions.
- **Steps:** read "Nothing needs you" strip → P&L by window (Today/Week/Month/Year) → capital
  deployed vs idle → four position quick-rows → leave.
- **End:** no action taken; this is the intended 90%-of-days flow.

### H2 — Review a pending proposal (read only)
- **Entry:** Home with ≥1 pending card.
- **Steps:** read card header (type label, action, sub-line, deadline chip) → headline figure
  (annualized % vs the APR gate) → key-value grid (premium, IV rank, assignment odds) →
  **tap "Why the engine wants this (5)"** to expand the reasons list → collapse again.
- **Branches:** on the vega-trigger card the reasons list is expanded by default; the
  attribution split bar (delta % vs vega %) is read instead of a headline figure.
- **End:** user is informed; card is still pending.
- **Under review:** the disclosure uses `▸`/`▾` text glyphs and does carry an
  expand/collapse click label; the attribution split is colour-coded (vega tint vs alarm tint)
  with the percentage also in text.

### H3 — Approve an entry proposal
- **Entry:** Home, entry card.
- **Steps:** (optionally H2 first) → tap **Approve** (the primary, filled button) → POST →
  full refresh → the resolved card disappears from the list and the nav badge count drops.
- **Branches:** POST fails → logged, **no UI feedback, card stays**. There is no in-flight
  state on the button and no confirmation step.
- **End:** card gone. That disappearance is the only success signal.
- **Under review:** a destructive-in-effect, single-tap, unconfirmed action whose entire
  outcome is announced by content vanishing.

### H4 — Veto a proposal (reason-gated)
The longest flow in the app, and the only form.
- **Entry:** Home. **Veto** on an entry card, or **Hold** on a vega-trigger card — both are
  `kind: reject`, both route here.
- **Steps:**
  1. Tap Veto/Hold → a modal bottom sheet slides up (drag handle, scrim).
  2. Read the title "Why veto this?" and the explanation of why the text matters.
  3. Focus the single free-text field (placeholder "What did the engine miss?"); soft keyboard
     opens over the sheet.
  4. Type a reason. **"Confirm veto" is disabled while the field is blank** and enables on the
     first non-whitespace character.
  5. Tap **Confirm veto** → sheet dismisses → POST with the reason → refresh → card disappears.
- **Alternates (all abandon the veto):** tap **Cancel**, tap the scrim, system back, or drag
  the sheet down. Typed text is discarded; reopening starts empty.
- **Also branches on failure:** the post fails → logged, no message, and the typed reason
  is lost.
- **Note for the review:** the sheet always says *veto* even when opened by the **Hold**
  button — deliberate, per the card's own footnote, but it means the button label and the
  dialog title do not match.
- **Under review:** focus movement into and out of the modal, keyboard occlusion of the
  Confirm/Cancel row, the disabled-until-valid button (dimmed to 40% alpha, no stated reason),
  and dismiss-by-scrim losing input.

### H5 — Snooze a decision
- **Entry:** Home, any card with a Snooze option.
- **Steps:** tap **Snooze** (ghost-styled, narrower than its siblings) → immediate POST →
  refresh → card leaves the list.
- **Branches:** post fails → logged, no message, card stays. Otherwise none: no confirmation,
  no undo, no indication of how long a snooze lasts.

### H6 — Resolve a stop-trigger (vega) card
- **Entry:** Home, vega-trigger card (stop hit, but the adverse move is vega-driven).
- **Steps:** read the reasons (already expanded) → read the attribution split → choose one of
  **Exit now** / **Hold** / **Snooze**.
- **Branches:** Exit now → H3-shaped single tap. Hold → H4 (reason sheet). Snooze → H5.
  Any of the three can fail silently, as in H3.
- **Note:** this card has **no default option**, so no button is primary-styled — the three
  buttons carry equal visual weight by design.
- **Under review:** the hold-vs-exit judgment depends entirely on the attribution split, which
  is a coloured bar plus text; deadline chip is "hot" styled (warn tint) on this card type.

### H7 — Choose a post-loss branch
- **Entry:** Home, post-loss branch card.
- **Steps:** read the loss and its attribution → read three branch rows, each a radio glyph +
  label + one-line detail, one carrying an "ENGINE'S PICK" tag → **tap a row to select it**
  (row fills and its border thickens) → **Confirm choice** enables → tap it → POST → refresh.
- **Branches:** selection can be changed any number of times before confirming; nothing is
  pre-selected, and the footnote says so. Confirm can fail silently, as in H3.
- **Under review:** the rows are a radio group in behaviour but are built as clickable rows
  with a button click-label — no selection semantics, no group semantics; selected state is
  conveyed by fill + border weight + a drawn dot.

### H8 — Go from Home to the full board
- **Entry:** Home, question 4 header, the **"All ›"** link.
- **Status: dead control.** It is clickable but the handler is not wired up in MainActivity, so
  tapping does nothing. The working path to the board is the bottom nav (G2).
- The **"Deadline order"** link on the question-1 header is a label only and is not clickable.

---

## 3. Positions flows

### P1 — Scan the board
- **Entry:** bottom nav → Positions.
- **Steps:** read the three aggregate cells (Margin, Net exposure, Syn. cash — each a figure
  over a thin meter bar) → section header with underlying count and banded total → one row per
  underlying → closing band footnote.
- **Each row, top to bottom:** ticker + contract identity + total P&L · economics line(s)
  (credit → current mark, stop multiple) · management line (wheel-stage dots, state label,
  exit-clock chip, distance-to-strike bar + %, attribution mark).
- **Under review:** the densest surface in the app; meaning is carried by dots, bars, chips and
  colour thresholds (plain → warn at 1.5× → alarm/vega at 2×) alongside the text.

### P2 — Drill into a position and come back
- **Entry:** Positions board, a row **that has detail data** (MSFT today).
- **Steps:** tap the row → Position detail for that ticker → read: header, economics grid, stop
  meter, unrealized row, auto-exit note, IV context (entry → now), P&L attribution split,
  position facts (stock price, distance to strike, delta, margin consumed, assignment odds) →
  **system back / back gesture** → board.
- **Branches:** bottom nav from detail also exits it (G2). The band chip works here and is
  again independent of the board's.
- **Note for the review:** there is **no on-screen back affordance** — the app bar holds only
  the title and the band chip. Back is handled, but only by the system control.

### P3 — Tap a row with no detail
- **Entry:** Positions board, any row other than MSFT.
- **Steps:** tap → **nothing happens.**
- **Note:** every row is clickable and every row announces the click label "Open *TICKER*
  detail", including the ones that do not navigate.

---

## 4. Risk flows

### R1 — Read the exposure picture
- **Entry:** bottom nav → Risk.
- **Steps:** read the "if everything assigns at once" hero (cost vs available cash+margin) →
  concentration bars by underlying → the largest-name question line.
- **Note:** money here is shown with no band chip on the screen to label it.

### R2 — Engage or release the kill switch
- **Entry:** Risk, kill-switch box at the bottom of the scroll.
- **Steps:** read the copy ("Pausing stops the engine opening new positions. It **keeps
  managing exits** …") → tap **Pause new entries** → POST round-trip → on success the button
  inverts to a filled "Resume new entries" **and the paused banner appears in the chrome of
  every screen**.
- **Branches:** POST fails → logged, button does not change, **no message**. There is no
  confirmation dialog — one tap arms it, by requirement.
- **Reverse:** tap **Resume new entries** → the paused banner clears everywhere.
- **Under review:** the screen supports an in-flight state ("Pausing…", button disabled) but
  the host never passes the in-flight flag, so it never shows; between tap and server reply the
  button looks untouched.

---

## 5. Flow variants worth walking twice

A flow can look different enough in these states to count as a separate pass.
`flow-variants.csv` expands this table into the 38 (flow × variant) passes the review should
actually walk — P1 alone accounts for six of them.


| Variant | How to reach it | Affects |
|---|---|---|
| Quiet vs busy Home | 0 vs ≥1 pending decisions | H1 vs H2–H7; position list truncates to 3 |
| Paused | R2 | Every flow (banner in chrome) |
| Each band | G3 | Every money figure on Home / Positions / Detail |
| Healthy vs at-stop position | MSFT (2.1× stop) vs a plain row | P1, P2 — colour escalation |
| Vega- vs delta-driven adverse move | MSFT vs AMD | H6, H7, P1, P2 — the tint vocabulary |
| Two-leg position (shares + call) | AMD row | P1 — per-leg P&L breakout |
| Cash-only position | NVDA row | P1 — "no open contracts", em-dash P&L |
| Offline / fetch failure | server down | G1, H3–H7, R2 — all currently silent |

---

## 6. Known dead ends and gaps (flagged, not flows)

- **"All ›"** on Home question 4 — clickable, does nothing (H8).
- **Non-MSFT board rows** — clickable and labelled, do not navigate (P3).
- **No refresh path** — one fetch at launch; the freshness figure is frozen after it.
- **No error, retry, or offline state anywhere** — every network failure is silent. The screens
  contain an offline note and a "cannot act while offline" mode, but the host never enables it.
- **No in-flight feedback** on any action (approve, veto, snooze, branch confirm, kill switch).
- **Journal, Backtests, Settings, notifications, widget** — not built; the bottom nav
  deliberately omits the unbuilt sections rather than showing dead tabs.
