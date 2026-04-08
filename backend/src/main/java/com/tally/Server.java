package com.tally;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

public class Server {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("BACKEND_PORT", "8080"));
    private TallyService tallyService = new TallyService();
    private DataStore dataStore = new DataStore();
    private Gson gson = new Gson();

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/ledgers", new LedgersHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port " + PORT);
        System.out.println("Endpoints: GET /ledgers or /ledgers?date=YYYY-MM-DD");
    }

    class LedgersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    // Extract date parameter from query string
                    String query = exchange.getRequestURI().getQuery();
                    String date = null;
                    if (query != null && query.contains("date=")) {
                        date = query.split("date=")[1].split("&")[0];
                    }
                    
                    List<Map<String, Object>> ledgers;
                    
                    if (date != null && !date.isEmpty()) {
                        System.out.println("Fetching ledgers for date: " + date);
                        ledgers = dataStore.loadLedgers();
                        if (ledgers == null) {
                            try {
                                ledgers = tallyService.fetchLedgersWithDate(date);
                                dataStore.saveLedgers(ledgers);
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("Error fetching from Tally: " + e.getMessage());
                                System.out.println("Using mock data for testing...");
                                ledgers = dataStore.getMockLedgers();
                            }
                        }
                    } else {
                        System.out.println("Fetching all ledgers without date filter");
                        ledgers = dataStore.loadLedgers();
                        if (ledgers == null) {
                            try {
                                ledgers = tallyService.fetchLedgers();
                                dataStore.saveLedgers(ledgers);
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("Error fetching from Tally: " + e.getMessage());
                                System.out.println("Using mock data for testing...");
                                ledgers = dataStore.getMockLedgers();
                            }
                        }
                    }
                    
                    String response = gson.toJson(ledgers);
                    System.out.println("Sending response: " + response);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (Exception e) {
                    System.out.println("Error in GET handler: " + e.getMessage());
                    e.printStackTrace();
                    try {
                        String errorResponse = gson.toJson(Map.of("error", e.getMessage()));
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(errorResponse.getBytes());
                        os.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            } else if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    // Assume JSON body with updated ledgers
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    System.out.println("Received POST: " + body);
                    List<Map<String, Object>> ledgers = gson.fromJson(body, List.class);
                    dataStore.saveLedgers(ledgers);
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().close();
                } catch (Exception e) {
                    System.out.println("Error in POST handler: " + e.getMessage());
                    e.printStackTrace();
                    try {
                        String errorResponse = gson.toJson(Map.of("error", e.getMessage()));
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(errorResponse.getBytes());
                        os.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            } else {
                exchange.sendResponseHeaders(405, 0);
                exchange.getResponseBody().close();
            }
        }
    }
}