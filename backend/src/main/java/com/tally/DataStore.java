package com.tally;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataStore {
    private static final String FILE_PATH = "ledgers.json";
    private Gson gson = new Gson();

    public List<Map<String, Object>> loadLedgers() {
        try (FileReader reader = new FileReader(FILE_PATH)) {
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            return gson.fromJson(reader, listType);
        } catch (Exception e) {
            return null;
        }
    }

    public void saveLedgers(List<Map<String, Object>> ledgers) {
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            gson.toJson(ledgers, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> getMockLedgers() {
        List<Map<String, Object>> ledgers = new ArrayList<>();
        
        // Add sample ledgers with opening, debit, credit, closing balances
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
            ledger.put("closing", data[5]);
            ledger.put("assigned", "");
            ledger.put("comment", "");
            ledger.put("date", "");
            ledgers.add(ledger);
        }
        
        return ledgers;
    }
}