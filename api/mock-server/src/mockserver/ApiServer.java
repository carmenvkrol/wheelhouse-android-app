package mockserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static mockserver.Json.obj;

/**
 * The mock's only state that actually mutates: whether new entries are paused, and
 * which decisions are still pending. Everything else (positions, pnl, risk) is static
 * fixture data — a real engine would move these too, but reproducing that logic is out
 * of scope for a client-facing mock (see api/mock-server/README.md).
 */
final class ServerState {
    final AtomicBoolean paused = new AtomicBoolean(false);
    final List<Map<String, Object>> decisions = new CopyOnWriteArrayList<>(Fixtures.seedDecisions());
}

final class ApiServer {
    private final ServerState state = new ServerState();

    void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/v1/status", this::handleStatus);
        server.createContext("/v1/pnl", this::handlePnl);
        server.createContext("/v1/risk", this::handleRisk);
        server.createContext("/v1/positions", this::handlePositions);
        server.createContext("/v1/decisions", this::handleDecisions);
        server.createContext("/v1/pause", exchange -> handlePauseResume(exchange, true));
        server.createContext("/v1/resume", exchange -> handlePauseResume(exchange, false));
        server.createContext("/v1/device", this::handleDevice);
        server.setExecutor(null);
        server.start();
        System.out.println("WheelHouse mock API listening on http://127.0.0.1:" + port);
        System.out.println("Endpoints: GET /v1/status, /v1/pnl, /v1/risk, /v1/positions, /v1/decisions");
        System.out.println("           POST /v1/decisions/{id}, /v1/pause, /v1/resume, /v1/device");
    }

    // ── Route handlers ─────────────────────────────────────────────────────

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!allowMethod(exchange, "GET")) return;
        respondJson(exchange, 200, obj(
            "engine", "ok",
            "worker_heartbeat_age_s", 30,
            "data_age_s", 42,
            "paused", state.paused.get(),
            "books", Json.arr("BASE")
        ));
    }

    private void handlePnl(HttpExchange exchange) throws IOException {
        if (!allowMethod(exchange, "GET")) return;
        respondJson(exchange, 200, Fixtures.pnl());
    }

    private void handleRisk(HttpExchange exchange) throws IOException {
        if (!allowMethod(exchange, "GET")) return;
        respondJson(exchange, 200, Fixtures.risk());
    }

    private void handlePositions(HttpExchange exchange) throws IOException {
        if (!allowMethod(exchange, "GET")) return;
        respondJson(exchange, 200, Fixtures.positions());
    }

    /** GET /v1/decisions (list pending) and POST /v1/decisions/{id} (resolve one). */
    private void handleDecisions(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) return;
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String remainder = path.equals("/v1/decisions") ? "" : path.substring("/v1/decisions/".length());

        if (method.equals("GET") && remainder.isEmpty()) {
            List<Object> pending = new java.util.ArrayList<>();
            for (Map<String, Object> d : state.decisions) {
                if ("pending".equals(d.get("status"))) pending.add(d);
            }
            respondJson(exchange, 200, pending);
            return;
        }

        if (method.equals("POST") && !remainder.isEmpty()) {
            resolveDecision(exchange, remainder);
            return;
        }

        respondJson(exchange, 404, obj("error", "not found"));
    }

    private void resolveDecision(HttpExchange exchange, String id) throws IOException {
        Map<String, String> body = Json.parseFlatObject(readBody(exchange));
        String optionId = body.get("option_id");
        String reason = body.get("reason");

        Map<String, Object> decision = null;
        for (Map<String, Object> d : state.decisions) {
            if (d.get("id").equals(id)) { decision = d; break; }
        }
        if (decision == null) {
            respondJson(exchange, 404, obj("error", "no such decision: " + id));
            return;
        }
        if (!"pending".equals(decision.get("status"))) {
            respondJson(exchange, 409, obj("error", "decision already resolved", "status", decision.get("status")));
            return;
        }
        if (optionId == null) {
            respondJson(exchange, 400, obj("error", "option_id is required"));
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) decision.get("options");
        Map<String, Object> chosen = null;
        for (Map<String, Object> o : options) {
            if (o.get("id").equals(optionId)) { chosen = o; break; }
        }
        if (chosen == null) {
            respondJson(exchange, 400, obj("error", "unknown option_id: " + optionId));
            return;
        }
        boolean requiresReason = Boolean.TRUE.equals(chosen.get("requires_reason"));
        if (requiresReason && (reason == null || reason.trim().isEmpty())) {
            respondJson(exchange, 400, obj("error", "reason is required for option " + optionId));
            return;
        }

        decision.put("status", "resolved:" + optionId);
        if (reason != null) decision.put("resolution_reason", reason);
        respondJson(exchange, 200, obj(
            "id", id, "status", decision.get("status"), "option_id", optionId
        ));
    }

    private void handlePauseResume(HttpExchange exchange, boolean pause) throws IOException {
        if (!allowMethod(exchange, "POST")) return;
        state.paused.set(pause);
        respondJson(exchange, 200, obj("paused", state.paused.get()));
    }

    private void handleDevice(HttpExchange exchange) throws IOException {
        if (!allowMethod(exchange, "POST")) return;
        Map<String, String> body = Json.parseFlatObject(readBody(exchange));
        respondJson(exchange, 200, obj("ok", true, "push_token_received", body.get("push_token") != null));
    }

    // ── Plumbing ────────────────────────────────────────────────────────────

    /** Returns false (and has already responded) if this wasn't a preflight or the expected method. */
    private boolean allowMethod(HttpExchange exchange, String expected) throws IOException {
        if (isPreflight(exchange)) return false;
        if (!exchange.getRequestMethod().equals(expected)) {
            respondJson(exchange, 405, obj("error", "method not allowed, expected " + expected));
            return false;
        }
        return true;
    }

    /** Handles CORS preflight; returns true if this request was one (caller should stop). */
    private boolean isPreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if (exchange.getRequestMethod().equals("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void addCorsHeaders(HttpExchange exchange) {
        // Permissive on purpose — this is a local dev mock any of the three clients
        // (Android, iOS, web) should be able to reach without fuss.
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respondJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
