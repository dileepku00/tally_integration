package com.tally;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class TallyService {

    private static final String TALLY_URL = System.getenv().getOrDefault("TALLY_URL", "http://localhost:9000"); // Configurable Tally URL

    public List<Map<String, Object>> fetchLedgers() throws Exception {
        // Fetch masters for all ledgers and groups
        String mastersXml = fetchFromTally(getMastersRequest());
        Map<String, String> allLedgers = parseAllLedgers(mastersXml);
        if (allLedgers.isEmpty()) {
            System.out.println("No ledgers found using List of Accounts, trying All Masters fallback");
            mastersXml = fetchFromTally(getMastersFallbackRequest());
            allLedgers = parseAllLedgers(mastersXml);
        }

        // Fetch trial balance for balances
        String balanceXml = fetchFromTally(getTrialBalanceRequest());
        Map<String, Double> ledgerBalances = parseLedgerBalances(balanceXml);

        // Combine
        List<Map<String, Object>> ledgers = new ArrayList<>();
        for (String ledger : allLedgers.keySet()) {
            Map<String, Object> l = new HashMap<>();
            l.put("name", ledger);
            l.put("group", allLedgers.get(ledger));
            l.put("balance", ledgerBalances.getOrDefault(ledger, 0.0));
            l.put("assigned", "");
            l.put("comment", "");
            l.put("date", "");
            ledgers.add(l);
        }
        return ledgers;
    }

    public List<Map<String, Object>> fetchLedgersWithDate(String asOnDate) throws Exception {
        // Fetch masters for all ledgers and groups
        String mastersXml = fetchFromTally(getMastersRequest());
        Map<String, String> allLedgers = parseAllLedgers(mastersXml);
        if (allLedgers.isEmpty()) {
            System.out.println("No ledgers found using List of Accounts, trying All Masters fallback");
            mastersXml = fetchFromTally(getMastersFallbackRequest());
            allLedgers = parseAllLedgers(mastersXml);
        }

        // Fetch detailed ledger report with opening, debit, credit, closing
        String detailedXml = fetchFromTally(getDetailedLedgerRequest(asOnDate));
        Map<String, Map<String, Double>> ledgerDetails = parseDetailedLedgers(detailedXml);
        if (ledgerDetails.isEmpty()) {
            System.out.println("Detailed ledger request failed or returned no data; using trial balance fallback");
            String balanceXml = fetchFromTally(getTrialBalanceRequest());
            Map<String, Double> ledgerBalances = parseLedgerBalances(balanceXml);
            for (String ledger : allLedgers.keySet()) {
                Map<String, Double> details = new HashMap<>();
                details.put("opening", 0.0);
                details.put("debit", 0.0);
                details.put("credit", 0.0);
                details.put("closing", ledgerBalances.getOrDefault(ledger, 0.0));
                ledgerDetails.put(ledger, details);
            }
        }

        // Combine
        List<Map<String, Object>> ledgers = new ArrayList<>();
        for (String ledger : allLedgers.keySet()) {
            Map<String, Object> l = new HashMap<>();
            l.put("name", ledger);
            l.put("group", allLedgers.get(ledger));
            
            Map<String, Double> details = ledgerDetails.getOrDefault(ledger, new HashMap<>());
            l.put("opening", details.getOrDefault("opening", 0.0));
            l.put("debit", details.getOrDefault("debit", 0.0));
            l.put("credit", details.getOrDefault("credit", 0.0));
            l.put("closing", details.getOrDefault("closing", 0.0));
            
            l.put("assigned", "");
            l.put("comment", "");
            l.put("date", "");
            ledgers.add(l);
        }
        return ledgers;
    }

    private String fetchFromTally(String xmlRequest) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TALLY_URL))
                .header("Content-Type", "text/xml")
                .POST(HttpRequest.BodyPublishers.ofString(xmlRequest))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Tally Response: " + response.body());
        return response.body();
    }

    private String getMastersRequest() {
        return "<ENVELOPE>" +
                "<HEADER>" +
                "<VERSION>1</VERSION>" +
                "<TALLYREQUEST>EXPORT</TALLYREQUEST>" +
                "</HEADER>" +
                "<BODY>" +
                "<DESC>" +
                "<REPORTNAME>List of Accounts</REPORTNAME>" +
                "<STATICVARIABLES>" +
                "<SVEXPORTFORMAT>XML</SVEXPORTFORMAT>" +
                "</STATICVARIABLES>" +
                "</DESC>" +
                "</BODY>" +
                "</ENVELOPE>";
    }

    private String getTrialBalanceRequest() {
        return "<ENVELOPE>" +
                "<HEADER>" +
                "<VERSION>1</VERSION>" +
                "<TALLYREQUEST>EXPORT</TALLYREQUEST>" +
                "</HEADER>" +
                "<BODY>" +
                "<DESC>" +
                "<REPORTNAME>Trial Balance</REPORTNAME>" +
                "<STATICVARIABLES>" +
                "<SVEXPORTFORMAT>XML</SVEXPORTFORMAT>" +
                "<EXPLODEALL>Yes</EXPLODEALL>" +
                "</STATICVARIABLES>" +
                "</DESC>" +
                "</BODY>" +
                "</ENVELOPE>";
    }

    private String getDetailedLedgerRequest(String asOnDate) {
        return "<ENVELOPE>" +
                "<HEADER>" +
                "<VERSION>1</VERSION>" +
                "<TALLYREQUEST>EXPORT</TALLYREQUEST>" +
                "</HEADER>" +
                "<BODY>" +
                "<DESC>" +
                "<REPORTNAME>Trial Balance</REPORTNAME>" +
                "<STATICVARIABLES>" +
                "<SVEXPORTFORMAT>XML</SVEXPORTFORMAT>" +
                "<EXPLODEALL>Yes</EXPLODEALL>" +
                "<ASONCHOICE>2</ASONCHOICE>" +
                "<ASONDATESELECTION>" + asOnDate + "</ASONDATESELECTION>" +
                "</STATICVARIABLES>" +
                "</DESC>" +
                "</BODY>" +
                "</ENVELOPE>";
    }

    private String getMastersFallbackRequest() {
        return "<ENVELOPE>" +
                "<HEADER>" +
                "<VERSION>1</VERSION>" +
                "<TALLYREQUEST>EXPORT</TALLYREQUEST>" +
                "</HEADER>" +
                "<BODY>" +
                "<DESC>" +
                "<REPORTNAME>All Masters</REPORTNAME>" +
                "<STATICVARIABLES>" +
                "<SVEXPORTFORMAT>XML</SVEXPORTFORMAT>" +
                "</STATICVARIABLES>" +
                "</DESC>" +
                "</BODY>" +
                "</ENVELOPE>";
    }

    private Map<String, String> parseAllLedgers(String xml) throws Exception {
        Map<String, String> ledgers = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        NodeList ledgerNodes = doc.getElementsByTagName("LEDGER");
        System.out.println("Found " + ledgerNodes.getLength() + " ledgers");
        for (int i = 0; i < ledgerNodes.getLength(); i++) {
            Element ledger = (Element) ledgerNodes.item(i);
            String name = getElementValue(ledger, "NAME");
            String parent = getElementValue(ledger, "PARENT");
            if (name != null && parent != null) {
                ledgers.put(name, parent);
            }
        }
        return ledgers;
    }

    private Map<String, Double> parseLedgerBalances(String xml) throws Exception {
        Map<String, Double> balances = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        NodeList dsplyname = doc.getElementsByTagName("DSPACCNAME");
        NodeList amounts = doc.getElementsByTagName("AMOUNT");
        System.out.println("Found " + dsplyname.getLength() + " balance entries");
        for (int i = 0; i < dsplyname.getLength(); i++) {
            String name = dsplyname.item(i).getTextContent();
            String amountStr = amounts.item(i).getTextContent();
            try {
                double amount = Double.parseDouble(amountStr);
                if (amount != 0) {
                    balances.put(name, amount);
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return balances;
    }

    private String getElementValue(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private Map<String, Map<String, Double>> parseDetailedLedgers(String xml) throws Exception {
        Map<String, Map<String, Double>> details = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        NodeList ledgers = doc.getElementsByTagName("LEDGER");
        System.out.println("Found " + ledgers.getLength() + " ledgers in detailed report");
        for (int i = 0; i < ledgers.getLength(); i++) {
            Element ledger = (Element) ledgers.item(i);
            String name = getElementValue(ledger, "NAME");
            String openingStr = getElementValue(ledger, "OPENINGBALANCE");
            String debitStr = getElementValue(ledger, "DEBITAMOUNT");
            String creditStr = getElementValue(ledger, "CREDITAMOUNT");
            String closingStr = getElementValue(ledger, "CLOSINGBALANCE");
            
            if (name != null) {
                Map<String, Double> ledgerDetails = new HashMap<>();
                try {
                    ledgerDetails.put("opening", openingStr != null ? Double.parseDouble(openingStr) : 0.0);
                    ledgerDetails.put("debit", debitStr != null ? Double.parseDouble(debitStr) : 0.0);
                    ledgerDetails.put("credit", creditStr != null ? Double.parseDouble(creditStr) : 0.0);
                    ledgerDetails.put("closing", closingStr != null ? Double.parseDouble(closingStr) : 0.0);
                } catch (NumberFormatException e) {
                    // Set defaults if parsing fails
                    ledgerDetails.put("opening", 0.0);
                    ledgerDetails.put("debit", 0.0);
                    ledgerDetails.put("credit", 0.0);
                    ledgerDetails.put("closing", 0.0);
                }
                details.put(name, ledgerDetails);
            }
        }
        return details;
    }
}