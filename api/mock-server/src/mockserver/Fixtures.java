package mockserver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static mockserver.Json.arr;
import static mockserver.Json.obj;

/**
 * Fixture data. Values are copied 1:1 from the Android app's sample*State() functions
 * (HomeDashboardScreen.kt, PositionsScreen.kt, PositionDetailScreen.kt, RiskScreen.kt) —
 * this is a mock server standing in for the not-yet-built real API, not the "fixtures
 * pack" api/fixtures/README.md describes (that one is backend-generated, Patrick's; this
 * is a hand-built dev tool serving contract-shaped JSON dynamically over HTTP so it's
 * reachable from Android, iOS, and web alike, not just bundled into one app).
 *
 * `exit.at`/decision `deadline` carry CONTRACT.md's literal field (an absolute
 * timestamp); `exit.days`/`deadline_label` are pre-formatted convenience fields — what
 * the Android client actually renders today, since it has no relative-time formatting
 * logic yet. Both are served so neither the client nor this mock has to invent the
 * other.
 */
final class Fixtures {
    private Fixtures() {}

    private static Map<String, Object> band(double floor, double policy, double mid) {
        return obj("floor", floor, "policy", policy, "mid", mid);
    }

    // ── /v1/pnl ─────────────────────────────────────────────────────────────

    static Map<String, Object> pnl() {
        Map<String, Object> unrealized = band(-320, -278, -255);
        return obj(
            "windows", obj(
                "today", obj("realized", band(214, 228, 239), "unrealized", unrealized),
                "wtd", obj("realized", band(686, 724, 751), "unrealized", unrealized),
                "mtd", obj("realized", band(2140, 2268, 2361), "unrealized", unrealized),
                "ytd", obj("realized", band(11480, 12190, 12704), "unrealized", unrealized)
            ),
            "target_capital", 200_000,
            "deployed", 86_400,
            "idle", 113_600
        );
    }

    // ── /v1/risk ────────────────────────────────────────────────────────────

    static Map<String, Object> risk() {
        return obj(
            "assign_all_cost", 86_400,
            "cash_margin_available", 112_000,
            "margin_utilization_pct", 62,
            // PositionsScreen's aggregates strip (margin/net exposure/synthetic cash) draws
            // from this same endpoint rather than a second one CONTRACT.md doesn't define —
            // net_exposure happens to equal assign_all_cost in the original wireframe data;
            // the two meter percentages are illustrative, same as the wireframe's own mock.
            "net_exposure", 86_400,
            "net_exposure_meter_pct", 77,
            "synthetic_cash", 41_200,
            "synthetic_cash_meter_pct", 38,
            "concentration", arr(
                obj("underlying", "TSLA", "pct", 31),
                obj("underlying", "MSFT", "pct", 24),
                obj("underlying", "GOOG", "pct", 18),
                obj("underlying", "AMD", "pct", 15),
                obj("underlying", "Other", "pct", 12)
            ),
            // Not yet consumed by any built screen — served empty rather than invented.
            "regime", obj()
        );
    }

    // ── /v1/positions ───────────────────────────────────────────────────────
    // One shape covers both PositionsScreen's board row and PositionDetailScreen's
    // drill-down: `detail` is non-null only for tickers with a built detail screen
    // (MSFT today) — CONTRACT.md doesn't define a separate per-position endpoint, and
    // adding one wasn't this mock's call to make.

    static List<Object> positions() {
        List<Object> list = new ArrayList<>();
        list.add(position("TSLA", 2,
            option("PUT", 290, "Aug 14", 18, 1, 6.15, band(4.73, 4.47, 4.34)),
            null, 7.6, "theta", exit("stop", 10), null));
        list.add(position("MSFT", 2,
            option("PUT", 430, "Jul 31", 4, 1, 3.10, band(6.50, 6.15, 5.98)),
            null, -2.1, "vega", exit("stop", 2), msftDetail()));
        list.add(position("AMD", 3,
            option("CALL", 165, "Aug 7", 11, 1, 2.85, band(3.75, 3.51, 3.37)),
            stock(100, 159.20, 158.00), -4.2, "delta", exit("expiry", 11), null));
        list.add(position("GOOG", 2,
            option("PUT", 175, "Aug 21", 25, 1, 2.10, band(1.22, 1.13, 1.06)),
            null, 3.4, "theta", exit("stop", 11), null));
        list.add(position("NVDA", 1, null, null, null, null, null, null));
        return list;
    }

    private static Map<String, Object> position(
        String ticker, int stage, Map<String, Object> option, Map<String, Object> stock,
        Double distancePct, String attribution, Map<String, Object> exit, Map<String, Object> detail
    ) {
        return obj(
            "ticker", ticker,
            "stage", stage,
            "option", option,
            "stock", stock,
            "distance_pct", distancePct,
            "attribution", attribution,
            "exit", exit,
            "detail", detail
        );
    }

    private static Map<String, Object> option(
        String right, int strike, String expiry, int dte, int qty, double credit, Map<String, Object> mark
    ) {
        return obj(
            "right", right, "strike", strike, "expiry", expiry, "dte", dte,
            "qty", qty, "credit", credit, "mark", mark
        );
    }

    private static Map<String, Object> stock(int qty, double basis, double last) {
        return obj("qty", qty, "basis", basis, "last", last);
    }

    /** `at` is the CONTRACT.md field (illustrative, relative to server start); `days` is
     *  what the Android client actually renders today. */
    private static Map<String, Object> exit(String type, int days) {
        return obj("type", type, "at", relativeIsoTime(days * 24L * 3600), "days", days);
    }

    private static Map<String, Object> msftDetail() {
        return obj(
            "entered_date", "Jul 15",
            "stock_price", 421.0,
            "delta", -0.52,
            "margin_consumed", 6_900,
            "assignment_odds_pct", 52,
            "iv", obj("at_entry", 54, "now", 81),
            "attribution_split", obj(
                "driver", "vega", "delta_pct", 22, "vega_pct", 78, "vega_label", "theta + vega",
                "note", "The mark is adverse because options got expensive, not because the stock " +
                    "broke. It sits 2.1% above strike — the thesis is intact."
            ),
            "stop_note", "This is the number the 2× stop watches — it has already crossed. " +
                "Attribution is vega-dominant, so the gate matrix raised a decision card instead of selling.",
            "auto_exit", obj(
                "title", "Day-14 stop in 2 days",
                "body", "Fires Jul 29, ahead of the Jul 31 expiry — but the 2× stop above got there " +
                    "first, and its card is open."
            )
        );
    }

    /** Illustrative-only: an offset from server start, formatted as ISO-8601 UTC. */
    static String relativeIsoTime(long secondsFromNow) {
        java.time.Instant instant = java.time.Instant.now().plusSeconds(secondsFromNow);
        return instant.toString();
    }

    // ── /v1/decisions — seed data (mutable at runtime via ServerState) ────────

    static List<Map<String, Object>> seedDecisions() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(vegaTriggerDecision());
        list.add(entryDecision());
        list.add(branchDecision());
        return list;
    }

    private static Map<String, Object> vegaTriggerDecision() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "d-2026-07-27-002");
        card.put("book", "BASE");
        card.put("type", "hold_or_exit");
        card.put("underlying", "MSFT");
        card.put("action", "MSFT $430 put hit its 2× stop");
        card.put("sub", "Sold $3.10 · now $6.50 to close — 2.10× credit · 4 DTE");
        card.put("attribution_split", obj(
            "driver", "vega", "delta_pct", 22, "vega_pct", 78, "vega_label", "IV expansion",
            "note", "IV rank 54 → 81 since entry, stock still 2.1% above strike. Adverse mark, " +
                "thesis intact — the rubric's vega exception."
        ));
        card.put("reasons", arr(
            "Stop-loss triggered at 2× credit — but attribution is vega-dominant",
            "Vega-driven triggers raise a card instead of auto-exiting (gate matrix)",
            "Recorded habit: these were held \"more aggressively or for longer than usual\""
        ));
        card.put("reasons_open_by_default", true);
        card.put("deadline", relativeIsoTime(41 * 60));
        card.put("deadline_label", "41m");
        card.put("deadline_hot", true);
        card.put("options", arr(
            option3("exit_now", "Exit now", "accept", false, false, null),
            option3("hold", "Hold", "reject", false, true, null),
            option3("snooze", "Snooze", "defer", false, false, null)
        ));
        card.put("foot_note", "\"Hold\" is kind: reject — it counts toward veto rate without the " +
            "word ever appearing. No is_default: the engine is escalating a conflict, not recommending.");
        card.put("status", "pending");
        return card;
    }

    private static Map<String, Object> entryDecision() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "d-2026-07-27-003");
        card.put("book", "BASE");
        card.put("type", "open_put");
        card.put("underlying", "TSLA");
        card.put("action", "Sell 1 TSLA $290 put");
        card.put("sub", "expires Aug 14 · 18 DTE");
        card.put("annualized_floor_pct", 21.4);
        card.put("apr_gate_pct", 20.0);
        card.put("premium", band(6.15, 6.28, 6.40));
        card.put("iv_rank", 78);
        card.put("assignment_odds", 0.24);
        card.put("reasons", arr(
            "APR gate: 22.1% ≥ 20% target — (0.8 × premium) ÷ margin × 365/14",
            "Strike $290 sits below the 90-day trailing low of $296",
            "Price at 38th pct of 52wk range",
            "No earnings before expiry",
            "STAPLE tier · 1 contract · 2.9% of target capital"
        ));
        card.put("reasons_open_by_default", false);
        card.put("deadline", relativeIsoTime(2 * 3600 + 14 * 60));
        card.put("deadline_label", "2h 14m");
        card.put("deadline_hot", false);
        card.put("options", arr(
            option3("approve", "Approve", "accept", true, false, null),
            option3("veto", "Veto", "reject", false, true, null),
            option3("snooze", "Snooze", "defer", false, false, null)
        ));
        card.put("foot_note", null);
        card.put("status", "pending");
        return card;
    }

    private static Map<String, Object> branchDecision() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "d-2026-07-27-004");
        card.put("book", "BASE");
        card.put("type", "post_loss_branch");
        card.put("underlying", "AMD");
        card.put("action", "AMD closed at a loss — what next?");
        card.put("sub", "Exited $170 put at 2× credit · realized −$412 (floor)");
        card.put("attribution_split", obj(
            "driver", "delta", "delta_pct", 81, "vega_pct", 19, "vega_label", "IV",
            "note", "The stock slid through the strike. Not a vega mark — the thesis moved against us."
        ));
        card.put("deadline", relativeIsoTime(28 * 3600 + 4 * 60));
        card.put("deadline_label", "1d 04h");
        card.put("deadline_hot", false);
        card.put("options", arr(
            option3("reenter_aggressive", "Re-enter, more aggressive", "branch", true, false,
                "Sell a new put harvesting the post-drop IV, strike below the fresh 90-day low."),
            option3("cooldown", "Cooldown the name", "branch", false, false,
                "Bench AMD for the cooldown period. No new entries; existing legs still managed."),
            option3("walk_away", "Walk away", "branch", false, false,
                "Drop AMD from the active universe until manually re-added.")
        ));
        card.put("foot_note", "No reject kind on this card — so it contributes nothing to veto " +
            "rate, which is the right answer. is_default renders as a label here, not a pre-selection.");
        card.put("status", "pending");
        return card;
    }

    private static Map<String, Object> option3(
        String id, String label, String kind, boolean isDefault, boolean requiresReason, String detail
    ) {
        return obj(
            "id", id, "label", label, "kind", kind,
            "is_default", isDefault, "requires_reason", requiresReason, "detail", detail
        );
    }
}
