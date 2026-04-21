package com.tally;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataStore {
    private static final String LEGACY_FILE_PATH = "ledgers.json";
    private static final String SETTINGS_FILE_PATH = "settings.json";
    private static final String LEDGER_META_FILE_PATH = "ledger_meta.json";

    private final Gson gson = new Gson();

    public Map<String, Object> loadSettings() {
        Map<String, Object> defaults = defaultSettings();
        try (FileReader reader = new FileReader(SETTINGS_FILE_PATH)) {
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> loaded = gson.fromJson(reader, mapType);
            if (loaded != null) {
                defaults.putAll(loaded);
            }
        } catch (Exception ignored) {
        }
        return defaults;
    }

    public void saveSettings(Map<String, Object> settings) {
        Map<String, Object> merged = defaultSettings();
        if (settings != null) {
            merged.putAll(settings);
        }
        try (FileWriter writer = new FileWriter(SETTINGS_FILE_PATH)) {
            gson.toJson(merged, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> loadLedgerMetadata(String firm, String date) {
        Map<String, List<Map<String, Object>>> store = loadLedgerMetadataStore();
        String key = buildContextKey(firm, date);
        return store.get(key);
    }

    public void saveLedgerMetadata(String firm, String date, List<Map<String, Object>> ledgers) {
        Map<String, List<Map<String, Object>>> store = loadLedgerMetadataStore();
        store.put(buildContextKey(firm, date), extractEditableMetadata(ledgers));
        try (FileWriter writer = new FileWriter(LEDGER_META_FILE_PATH)) {
            gson.toJson(store, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> mergeWithMetadata(
            List<Map<String, Object>> liveLedgers,
            List<Map<String, Object>> metadata,
            String firm,
            String date
    ) {
        if (liveLedgers == null) {
            liveLedgers = new ArrayList<>();
        }
        if (metadata == null || metadata.isEmpty()) {
            return ensureDefaults(liveLedgers, firm, date);
        }

        Map<String, Map<String, Object>> metadataByLedger = new HashMap<>();
        for (Map<String, Object> item : metadata) {
            metadataByLedger.put(buildLedgerKey(item), item);
        }

        for (Map<String, Object> ledger : liveLedgers) {
            Map<String, Object> saved = metadataByLedger.get(buildLedgerKey(ledger));
            applyDefaults(ledger, firm, date);
            if (saved == null) {
                continue;
            }
            ledger.put("accountTeamStatus", getString(saved.get("accountTeamStatus"), getDefaultTeamStatus(ledger)));
            ledger.put("pendingAt", getString(saved.get("pendingAt"), ""));
            ledger.put("assigned", getString(saved.get("assigned"), ""));
            ledger.put("comment", getString(saved.get("comment"), ""));
            ledger.put("date", getString(saved.get("date"), date));
            ledger.put("comments", saved.get("comments") instanceof List ? saved.get("comments") : new ArrayList<>());
            ledger.put("firm", getString(saved.get("firm"), firm));
        }
        return liveLedgers;
    }

    public List<Map<String, Object>> ensureDefaults(List<Map<String, Object>> ledgers, String firm, String date) {
        for (Map<String, Object> ledger : ledgers) {
            applyDefaults(ledger, firm, date);
        }
        return ledgers;
    }

    public List<Map<String, Object>> getMockLedgers(String firm, String date) {
        List<Map<String, Object>> ledgers = new ArrayList<>();
        Object[][] sampleData = {
            {"Cash", "Bank Accounts", 10000.00, 5000.00, 8000.00, 7000.00},
            {"Bank Account", "Bank Accounts", 50000.00, 20000.00, 15000.00, 55000.00},
            {"Receivables", "Current Assets", 25000.00, 12000.00, 5000.00, 32000.00},
            {"Sales", "Income", 0.00, 0.00, 500000.00, -500000.00},
            {"Expenses", "Expenses", 0.00, 150000.00, 0.00, 150000.00},
            {"Equipment", "Fixed Assets", 200000.00, 50000.00, 0.00, 250000.00},
            {"Payables", "Current Liabilities", -75000.00, 20000.00, 40000.00, -95000.00}
        };

        for (Object[] data : sampleData) {
            Map<String, Object> ledger = new HashMap<>();
            ledger.put("name", data[0]);
            ledger.put("group", data[1]);
            ledger.put("opening", data[2]);
            ledger.put("debit", data[3]);
            ledger.put("credit", data[4]);
            double closing = (Double) data[5];
            ledger.put("closing", closing);
            ledger.put("status", Math.abs(closing) < 0.01 ? "closed" : "open");
            applyDefaults(ledger, firm, date);
            ledgers.add(ledger);
        }
        return ledgers;
    }

    private List<Map<String, Object>> extractEditableMetadata(List<Map<String, Object>> ledgers) {
        List<Map<String, Object>> metadata = new ArrayList<>();
        if (ledgers == null) {
            return metadata;
        }

        for (Map<String, Object> ledger : ledgers) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", getString(ledger.get("name"), ""));
            entry.put("group", getString(ledger.get("group"), ""));
            entry.put("firm", getString(ledger.get("firm"), ""));
            entry.put("accountTeamStatus", getString(ledger.get("accountTeamStatus"), ""));
            entry.put("pendingAt", getString(ledger.get("pendingAt"), ""));
            entry.put("assigned", getString(ledger.get("assigned"), ""));
            entry.put("comment", getString(ledger.get("comment"), ""));
            entry.put("date", getString(ledger.get("date"), ""));
            entry.put("comments", ledger.get("comments") instanceof List ? ledger.get("comments") : new ArrayList<>());
            metadata.add(entry);
        }
        return metadata;
    }

    private Map<String, List<Map<String, Object>>> loadLedgerMetadataStore() {
        try (FileReader reader = new FileReader(LEDGER_META_FILE_PATH)) {
            Type type = new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType();
            Map<String, List<Map<String, Object>>> store = gson.fromJson(reader, type);
            return store != null ? store : new HashMap<>();
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private void applyDefaults(Map<String, Object> ledger, String firm, String date) {
        String status = getString(ledger.get("status"), Math.abs(getDouble(ledger.get("closing"))) < 0.01 ? "closed" : "open");
        ledger.put("status", status);
        ledger.put("firm", getString(ledger.get("firm"), firm));
        ledger.put("accountTeamStatus", getString(ledger.get("accountTeamStatus"), getDefaultTeamStatus(ledger)));
        ledger.put("pendingAt", getString(ledger.get("pendingAt"), ""));
        ledger.put("assigned", getString(ledger.get("assigned"), ""));
        ledger.put("comment", getString(ledger.get("comment"), ""));
        ledger.put("date", getString(ledger.get("date"), date));
        ledger.put("comments", ledger.get("comments") instanceof List ? ledger.get("comments") : new ArrayList<>());
    }

    private String getDefaultTeamStatus(Map<String, Object> ledger) {
        return "closed".equalsIgnoreCase(getString(ledger.get("status"), "")) ? "closed" : "work in progress";
    }

    private Map<String, Object> defaultSettings() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("host", "localhost");
        defaults.put("port", "9000");
        defaults.put("selectedFirm", "");
        defaults.put("tallyPath", "C:\\Program Files\\TallyPrime\\tally.exe");
        return defaults;
    }

    private String buildContextKey(String firm, String date) {
        return getString(firm, "").trim() + "|" + getString(date, "").trim();
    }

    private String buildLedgerKey(Map<String, Object> ledger) {
        return getString(ledger.get("name"), "") + "|" + getString(ledger.get("group"), "");
    }

    private String getString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private double getDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
