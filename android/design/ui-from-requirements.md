# WheelHouse Android — UI implied by REQUIREMENTS.md

**Read against REQUIREMENTS v0.8, CONTRACT v0 (+`/v1/pnl`, `next_auto_exit`, `urgency`), RUBRIC v0.1.2 gate matrix.**
**Rev 2 — folds in the PR #3 review: `options[]` adopted with a `kind` field, positions row re-ordered contract-first, benchmarks settled.**
Companion to `wireframes.html` in this folder. Section refs are to REQUIREMENTS.md unless noted.

The requirements doc hands UI design to the builder. So this is not a design — it's a reading of what the requirements *constrain*, what they *imply*, and where they leave a hole. Revision history at the bottom.

---

## 1. The four global elements

Four pieces of state must be visible or reachable everywhere. They compete for the same real estate, so settling them first makes every screen easier.

**Data age (§6.1, §7.1).** Required on every screen, with a stale banner past the engine's threshold. Fresh wants to be quiet (a timestamp line); stale wants to be loud (a banner that displaces content). One component with a severity switch, not two things.

**Band label (§6.7, CONTRACT band rule).** Every P&L-bearing number must say which of {floor, policy, mid} it is. Labelling each one individually drowns the UI in chips, so band is a **global mode**: one persistent control, defaulting to floor, applying app-wide, with individual values inheriting the label from a single source. Per-value chips survive only where a screen deliberately shows two bands at once. The widget (§6.6) is the hard case — no room for a word, so a three-character chip beside the number.

**Paused state** (`/v1/status.paused`). The kill switch lives on the risk screen, but a paused engine shown only there is the same species of bug as §7.1's silently-stale positions. Paused is global chrome.

**~~Active book~~** — resolved. BASE-only in v1 (§11), so no switcher and no book-scoping until TASTE arrives at v1.1. One less thing in the app bar; worth keeping the layout able to absorb it later.

---

## 2. Navigation

Five functional areas, five bottom-nav slots — but folding Decisions into the home dashboard (§6.2) frees one. That relieves the pressure worth flagging last round: **Backtests is a three-level browser used weekly at most**, and it had the weakest claim on a top-level slot. Settings (§6.8) is now homeless and is the other candidate for the freed slot.

Whether a separate full-height Decisions tab survives alongside the dashboard is explicitly the builder's call (§6.2). The argument against: if the dashboard already leads with deadline-sorted cards, a second surface showing the same cards is a navigation choice with no new information behind it. The argument for: a dashboard that scrolls past three cards into P&L and leverage is a poor place to *work through* a queue, and triage is a different task from glancing.

§7.5's cold-start target now names the home surface, which resolves the v0.3 inconsistency.

---

## 3. Home dashboard (§6.2)

Four questions in fixed order: decisions · what we've made · how leveraged · how positions are doing.

**Both states need designing, not just one.** §6.6 targets ~90% FYI pushes and says the app "succeeds when it earns *not being checked*" — so the no-decisions state is the app's most common face, and the reframe only works if it carries questions 2–4 with real weight rather than reading as an empty screen with clutter beneath it. Question 1 compresses to a single reassurance strip when the answer is no; cards dominate when it isn't.

**Question 3 needs a sentence, not just a bar.** Deployed vs. idle against target capital is where a plausible misreading lives: per-position APR runs ~21% while the success bar (§2) is 10% on total leveraged capital. Those look contradictory unless the screen says idle capital is the difference. Cheap to state, expensive to leave implied.

**Question 4 carries the auto-exit clock** (`next_auto_exit`) — day-14 stop or expiry, whichever fires first. It's the "do I need to care today" field, so it belongs in the compressed view, not only on the full board.

---

## 4. Decision cards

**The card component can no longer assume a shape.** The gate matrix adds two types beyond the original five, and both break the assumed pattern:

- **`hold_or_exit` (vega-driven trigger).** Attribution *is* the argument — the whole reason the card exists rather than the engine acting alone is that the adverse move came from IV expansion with the stock intact. So the delta/vega split takes the visual position `annualized_floor_pct` holds on an entry card.
- **Post-loss branch.** Three-way (re-enter aggressive / cooldown / walk away). No veto exists. Named options with consequences spelled out, single confirm.

**`options[]` is adopted** (PR #3 review). The card carries `options[]` of `{id, label, kind, is_default, requires_reason}` and `POST /v1/decisions/{id}` takes `{option_id, reason?}`. `kind` — one of `accept | reject | defer | branch` — was Patrick's addition, so that §2's veto-rate metric keeps a stable category to count while the labels stay the engine's own words. Right call, and it does more than protect the metric.

**What it buys the app.** All three cards now render from one function, which picks buttons or a radio list by asking whether any option has `kind: "branch"`. The card component stopped needing to know the type list at all — which was the actual defect, since that list has grown twice. The translation table is gone: the mapping line under each card now just prints ids and kinds, and nothing in the app has to stay in sync with the engine's vocabulary.

**Where `kind` earns itself immediately.** On the vega card, "Hold" is `kind: reject` — it counts toward veto rate although the word "veto" never appears on screen. On the branch card *no* option is a reject, so it contributes nothing to that metric, which is right: declining to re-enter a name is not a vote against the rubric. Under fixed verbs both cards would have POSTed `approve` and the metric would have quietly counted the wrong things in both directions. That is a stronger argument for `kind` than the one it was proposed with.

**`is_default` is a rendering hint, never a pre-selection** — and the branch card is redrawn accordingly. It now shows the engine's preference as an "engine's pick" label with nothing selected and Confirm disabled until the operator taps a branch. The earlier draft pre-selected it, which made a three-way judgment submittable in one tap.

The reason is §2 again. Veto rate is the automatability metric, and it only means anything if agreeing and disagreeing cost the operator the same. A pre-selected default makes agreeing one tap and disagreeing two — plus a written sentence wherever `requires_reason` applies — so the metric would decline because of button styling rather than because the rubric improved. That is the same failure mode as the veto chips: an affordance quietly manufacturing the data that grades the system. It also gets close to letting the engine decide a call the gate matrix deliberately withheld from it.

**The cut is not button-vs-radio, it's whether the engine has a recommendation at all.** Equal tap counts are not equal weight: on the vega card, "Exit now" rendered as the filled primary *and* "Hold" carries `requires_reason`, so agreeing cost one tap and disagreeing cost a tap plus a written sentence, with the emphasis pointing at the cheap option.

So the rule is per card, by what the engine actually knows:

- **Entry card** — the engine screened the trade and it cleared the APR gate. That is a real proposal, `is_default` is honest, and Approve stays primary.
- **Vega card** — this card exists *because* the 2× rule and the attribution disagree. The engine is escalating a conflict, not recommending. It sends no `is_default`, and "Exit now" and "Hold" render with equal weight.
- **Branch card** — no proposal at all, so the preference is a label and nothing is pre-selected.

The vega case has a sharper reason than symmetry. RUBRIC records that Patrick held these positions "more aggressively or for longer than usual," and names strict-stops vs. vega-hold as a **backtest A/B**. A card that nudges toward exiting biases the forward record of which arm gets chosen — the UI would be putting its thumb on the exact comparison the instrument exists to settle.

"Snooze" stays visually lighter on every card. Deferring isn't a third position on the substance, so lightening it isn't a thumb on the scale between the two real answers.

**The journal should record whether the submitted option was the default** (§6.5). Without it the audit trail cannot distinguish "weighed three branches and chose re-enter" from "tapped Confirm," and those are different events for anything §2 wants to measure. It also gives a cheap check on the nudge itself: if the default-agreement rate climbs while veto rate falls, the decline is coming from the UI, not the engine.

**Other card mechanics** (unchanged from the first reading): deadline is the organizing principle and drives sort order and escalation; approve requires a live round-trip, so it needs in-flight and failure states plus an explicit offline-disabled variant with a stated reason; `requires_reason` now generalises what used to be the hardcoded veto-reason rule.

**On the veto field** — chips dropped, per your instinct and Patrick's agreement. Replaced with prompts that insert nothing. Worth stating the *reason* in the sheet copy: §2 now judges automatability on whether veto rate **declines across rubric versions**, which only works if reasons are specific enough to diff into proposed rubric changes. "Too concentrated" is not diffable. Telling the operator that is the cheapest way to get a usable sentence.

---

## 5. Positions (§6.1)

**Re-ordered after the PR #3 review: the contract comes first.** The objection was right, and the diagnosis was more precise than "add some fields" — the row had the right content in the wrong rank. Opening with wheel dots, stop clock and attribution asks the operator to trust the engine's management story before the row has said what is actually owned. A position row that a wheel trader can't identify at a glance isn't a position row.

The row is now two registers, in this order:

1. **Contract identity and economics.** `MSFT · $430 PUT · Jul 31 · ×1`, then `sold $3.10 → now $6.50`, with unrealized P&L on the right.
2. **Engine management state.** Wheel marker, auto-exit clock, cost-to-close as a multiple of credit, distance %, attribution token.

**Cost-to-close pays for its space twice**, which is what settles it against the other candidates for the row. It is the gain/loss that matters on short premium, and it is the same number the 2× stop watches — so the row can show *why* a card exists rather than only that one does. MSFT reads `2.10×` on the board, and that is precisely the trigger that raised its `hold_or_exit` card. Stock price moved to detail as suggested; distance % carries most of what it was doing.

**One band subtlety this surfaces, and it belongs in the contract rule.** Credit received is settled cash — a fact, with no floor/policy/mid. Cost to close is a live quote and is fully banded. So on a short-premium row exactly one of the two numbers carries a band, and the unrealized P&L inherits its band from the close side alone. That looks like an exception to §6.7 but isn't; it's the band rule applied correctly to a number that has already happened. The board states it once in a footnote rather than labelling each value, consistent with the global band mode in §1.

**The covered-call row is the awkward case, and it's drawn honestly rather than hidden.** §6.1 wants one row per underlying, but a shares-plus-call state has two legs with different economics — and the Schwab-style row Patrick is comparing against is a single-contract row, so the model doesn't reach this case. AMD therefore gets two economics lines (call credit→mark, shares basis→last) and the row total on line 1 is their sum. A single `sold → now` there would imply one contract produced the whole number. The cash state (NVDA) has no contract line at all and degrades to wheel state and a dash.

**What this needs from `/v1/positions`, which currently doesn't name it.** The endpoint sketch reads "per-underlying wheel state, open legs, P&L bands, margin, next_auto_exit" — enough for the old row, not for this one. Per leg it needs contract identity (`right`, `strike`, `expiry`, `qty`), the **credit received at open**, and the **current cost to close** as a band. Stock legs need quantity and cost basis. Flagged rather than edited here, since `api/` is Patrick's — but the row cannot ship against the contract as written.

Three fields still earn non-numeric encoding on the management line:

- **Wheel state** — a lifecycle (CASH → short put → shares + covered call → called away), not a label. A four-step marker makes the board scannable in a way a text column never will.
- **Distance %** — the most risk information per pixel on the row.
- **Attribution** — §6.1 requires a red vega position look *visibly different* from one sliding toward its strike. Two tokens, deliberately unalike: a dashed blue `vega` mark and a solid red `Δ-driven` one. Same red number, different diagnosis, no reading required.

IV entry→now moved to detail. It's per-position context you consult, not a field you scan across rows, and it was the weakest earner once the economics arrived.

Detail now opens with an **economics block** — credit received against cost to close, plus a meter showing where the position sits against its 2× stop — and then the attribution split, which gets a bar and, importantly, **a sentence**. "78% vega" only means something adjacent to "stock sits 2.1% above strike." The number alone doesn't carry the judgment. (The old detail row reading "premium captured 88% of credit" was simply wrong for a position trading above its credit; showing the two numbers side by side is what made it obvious.)

One forward-looking note: §2's distrust rule runs this same machinery in reverse. Wins arriving via delta rather than theta+vega are a **red flag**, not a success. So the detail view eventually needs a profitable-but-suspicious state — an unusual thing for a P&L screen to have, and easy to forget until §2 is being evaluated.

---

## 6. Risk, journal, backtests, settings

**Risk (§6.4).** One dominant comparison — assign-all vs. available — because it's one relationship, not a table. Kill switch stays one tap and reversible; the copy must say pausing stops *entries, not exits*, or "paused" reads as "everything stopped." Concentration is worth posing as a question rather than reporting: TSLA at 31% with no per-name ceiling set is exactly the concentration-vs-yield tradeoff the instrument exists to measure.

**Journal (§6.5).** Chronological, filterable, with `refs` letting entries drill through to the decision or position they describe — that's what turns a log into an audit trail. Decision entries should also record **whether the submitted option was the engine's default** (§4) — one boolean that keeps a rubber stamp distinguishable from a considered choice, and lets the default-agreement rate be read alongside veto rate rather than hidden inside it.

**Backtests (§6.3).** Three levels plus compare-two-runs. The benchmark set grew to four (QQQ, cash, put-write, JEPQ), so five lines now share one chart at phone width. **Settled in the PR #3 review:** small multiples rather than one overlay, and the strategy-vs-JEPQ pair gets its own view, since §2 judges JEPQ-relative performance specifically. Not drawn yet — it lands in the backtests pass, which is also where the three-level browser gets designed properly.

**Settings (§6.8).** Now exists as a requirement. Contents: per-class notification preferences, band display default, device/account management. Presence is the requirement; it grows with the app.

---

## 7. Notifications (§6.6)

Three classes map onto Android notification channels, which is likely why they're a hard requirement — channels give per-class user control, and that's the mechanism making the ~90%-FYI target survive contact with a real user.

The triage model adds `urgency` to the payload: `deferrable` honors device DND, `page` interrupts. Engine-side escalation handles the case flagged in the first reading (a DECISION suppressed overnight expiring unseen), so the app's job narrows to honoring `urgency` and owning **one DND-bypass channel** — whose permission has to be requested explicitly and justified to the user.

Veto-from-notification needs a reason, so that's direct-reply input, not a plain action button. Approve still requires the live POST. Deep-link scheme remains the builder's call (§11), to be recorded in CONTRACT.md once chosen.

---

## 8. Open items

The five blocking questions from the first reading are all resolved (§11), and the PR #3 review closed two more — `options[]` (§4) and the benchmark chart (§6). What's left:

1. **Does a separate Decisions tab survive alongside the dashboard?** (§6.2, explicitly the builder's call.) Also decides what occupies the freed nav slot.
2. **Where Settings lives** — freed nav slot, overflow menu, or profile affordance.
3. **Deep-link scheme name** (§11) — still open, still the builder's call.

For the schema pass, two things the app now depends on:

4. **`/v1/positions` doesn't carry the fields the re-ordered row is built on** — per-leg contract identity, credit received at open, current cost to close as a band, and stock-leg basis. See §5. This one blocks the row against real payloads, not just fixtures.
5. **`is_default` semantics** — a recommendation the app renders, not a pre-selection it submits, and **sent only where the engine actually has a proposal** (§4). Entry cards carry one; vega and branch cards don't. Needs a line in the contract so the engine and app agree, plus a journal field recording whether the chosen option was the default.

Every rubric string in the wireframes is a paraphrase of RUBRIC.md. The fixtures pack replaces them with real payloads; the entry card's reason list is the element most likely to change shape when it lands, and the positions economics are now the second most likely.

---

## Revision history

| Date | Change |
|---|---|
| 2026-08-19 | Rev 2a: `is_default` settled — a rendering hint, never a pre-selection, and sent only where the engine has a genuine proposal. Branch card no longer pre-selects; vega card drops its default so Exit and Hold carry equal weight (it would otherwise bias RUBRIC's strict-stops vs. vega-hold A/B); journal gains a was-the-default flag (§4, §6.5). |
| 2026-08-19 | Rev 2, answering the PR #3 review: `options[]` adopted with Patrick's `kind` field and both new card types now rendering from it (§4); **positions row re-ordered contract-first** — identity + credit-vs-cost-to-close lead, engine management state demoted to a second line, stock price and IV context moved to detail (§5); benchmarks settled as small multiples + a JEPQ view (§6); §8 reopened with two contract dependencies the row change creates. |
| 2026-07-27 | Redrawn for REQUIREMENTS v0.8: home reframed as dashboard (§3), two new card types + the contract-verb problem (§4), attribution as a first-class visual (§5), notification triage (§7). All five original blocking questions resolved; §8 replaced with what remains. |
| 2026-07-27 | Initial reading against REQUIREMENTS v0.3 (PR #1). |
