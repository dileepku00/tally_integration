package com.tally;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("BACKEND_PORT", "8080"));
    private static final Path DIST_DIR = Path.of("..", "frontend", "dist").normalize();

    private final TallyService tallyService = new TallyService();
    private final DataStore dataStore = new DataStore();
    private final Gson gson = new Gson();

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/ledgers", new LedgersHandler());
        server.createContext("/api/settings", new SettingsHandler());
        server.createContext("/api/firms", new FirmsHandler());
        server.createContext("/api/tally/test", new TallyTestHandler());
        server.createContext("/api/tally/launch", new TallyLaunchHandler());
        server.createContext("/api/tally/debug-balances", new TallyDebugBalancesHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port " + PORT);
        System.out.println("Open http://localhost:" + PORT);
    }

    class LedgersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 200);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                    Map<String, Object> settings = dataStore.loadSettings();
                    String host = firstNonBlank(query.get("host"), asString(settings.get("host")), "localhost");
                    String port = firstNonBlank(query.get("port"), asString(settings.get("port")), "9000");
                    String firm = firstNonBlank(query.get("firm"), asString(settings.get("selectedFirm")), "");
                    String date = firstNonBlank(query.get("date"), "");

                    List<Map<String, Object>> liveLedgers;
                    String source = "live";
                    String message = "Live Tally data loaded.";
                    try {
                        liveLedgers = date.isEmpty()
                                ? tallyService.fetchLedgers(host, port, firm)
                                : tallyService.fetchLedgersWithDate(host, port, firm, date);
                    } catch (Exception liveError) {
                        List<Map<String, Object>> savedOnly = dataStore.loadLedgerMetadata(firm, date);
                        if (savedOnly != null && !savedOnly.isEmpty()) {
                            liveLedgers = dataStore.ensureDefaults(savedOnly, firm, date);
                            source = "saved";
                            message = "Loaded saved ledger follow-up data because live Tally fetch failed.";
                        } else {
                            liveLedgers = List.of();
                            source = "empty";
                            message = "Live Tally fetch failed and no saved ledger data was available.";
                        }
                    }

                    List<Map<String, Object>> metadata = dataStore.loadLedgerMetadata(firm, date);
                    List<Map<String, Object>> merged = dataStore.mergeWithMetadata(liveLedgers, metadata, firm, date);
                    exchange.getResponseHeaders().set("X-Tally-Source", source);
                    exchange.getResponseHeaders().set("X-Tally-Message", message);
                    sendJson(exchange, 200, merged);
                } catch (Exception e) {
                    sendJson(exchange, 500, Map.of("error", e.getMessage()));
                }
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                    Map<String, Object> settings = dataStore.loadSettings();
                    String firm = firstNonBlank(query.get("firm"), asString(settings.get("selectedFirm")), "");
                    String date = firstNonBlank(query.get("date"), "");
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    List<Map<String, Object>> ledgers = gson.fromJson(body, List.class);
                    dataStore.saveLedgerMetadata(firm, date, ledgers);
                    sendEmpty(exchange, 200);
                } catch (Exception e) {
                    sendJson(exchange, 500, Map.of("error", e.getMessage()));
                }
                return;
            }

            sendEmpty(exchange, 405);
        }
    }

    class SettingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 200);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, dataStore.loadSettings());
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> input = gson.fromJson(body, Map.class);
                    Map<String, Object> current = dataStore.loadSettings();
                    current.put("host", firstNonBlank(asString(input.get("host")), asString(current.get("host")), "localhost"));
                    current.put("port", firstNonBlank(asString(input.get("port")), asString(current.get("port")), "9000"));
                    current.put("selectedFirm", firstNonBlank(asString(input.get("selectedFirm")), ""));
                    current.put("tallyPath", firstNonBlank(asString(input.get("tallyPath")), asString(current.get("tallyPath")), ""));
                    dataStore.saveSettings(current);
                    sendJson(exchange, 200, current);
                } catch (Exception e) {
                    sendJson(exchange, 500, Map.of("error", e.getMessage()));
                }
                return;
            }

            sendEmpty(exchange, 405);
        }
    }

    class FirmsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 200);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 405);
                return;
            }

            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                Map<String, Object> settings = dataStore.loadSettings();
                String host = firstNonBlank(query.get("host"), asString(settings.get("host")), "localhost");
                String port = firstNonBlank(query.get("port"), asString(settings.get("port")), "9000");
                List<String> firms = tallyService.fetchCompanies(host, port);
                sendJson(exchange, 200, Map.of("firms", firms));
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    class TallyTestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 200);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 405);
                return;
            }

            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                Map<String, Object> settings = dataStore.loadSettings();
                String host = firstNonBlank(query.get("host"), asString(settings.get("host")), "localhost");
                String port = firstNonBlank(query.get("port"), asString(settings.get("port")), "9000");
                sendJson(exchange, 200, tallyService.testConnection(host, port));
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    class TallyLaunchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 200);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 405);
                return;
            }

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> input = gson.fromJson(body, Map.class);
                Map<String, Object> settings = dataStore.loadSettings();
                String tallyPath = firstNonBlank(asString(input.get("tallyPath")), asString(settings.get("tallyPath")), "");
                Map<String, Object> result = tallyService.launchTally(tallyPath);
                if (Boolean.TRUE.equals(result.get("launched"))) {
                    settings.put("tallyPath", tallyPath);
                    dataStore.saveSettings(settings);
                }
                sendJson(exchange, 200, result);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    class TallyDebugBalancesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 200);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendEmpty(exchange, 405);
                return;
            }

            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                Map<String, Object> settings = dataStore.loadSettings();
                String host = firstNonBlank(query.get("host"), asString(settings.get("host")), "localhost");
                String port = firstNonBlank(query.get("port"), asString(settings.get("port")), "9000");
                String firm = firstNonBlank(query.get("firm"), asString(settings.get("selectedFirm")), "");
                String date = firstNonBlank(query.get("date"), "");
                sendJson(exchange, 200, tallyService.debugBalances(host, port, firm, date));
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            sendJson(exchange, 200, Map.of("status", "ok", "port", PORT));
        }
    }

    class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            Path filePath = resolveStaticPath(requestPath);
            if (filePath == null || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                filePath = DIST_DIR.resolve("index.html");
            }

            if (!Files.exists(filePath)) {
                byte[] message = "Frontend build not found. Run `npm run build` inside the frontend folder.".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(200, message.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(message);
                }
                return;
            }

            String contentType = Files.probeContentType(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType != null ? contentType : "application/octet-stream");
            byte[] content = Files.readAllBytes(filePath);
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    private Path resolveStaticPath(String requestPath) {
        String cleanPath = requestPath == null || "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
        Path resolved = DIST_DIR.resolve(cleanPath).normalize();
        return resolved.startsWith(DIST_DIR) ? resolved : null;
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }

        for (String part : query.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            String[] pieces = part.split("=", 2);
            String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8);
            String value = pieces.length > 1 ? URLDecoder.decode(pieces[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] response = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
