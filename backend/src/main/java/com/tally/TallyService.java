package com.tally;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TallyService {

    public List<Map<String, Object>> fetchLedgers(String host, String port, String firm) throws Exception {
        Map<String, String> allLedgers = fetchAllLedgers(host, port, firm);

        String balanceXml = fetchFromTally(host, port, getTrialBalanceRequest(firm, null));
        Map<String, Double> ledgerBalances = parseLedgerBalances(balanceXml);
        if (ledgerBalances.isEmpty()) {
            String collectionXml = fetchFromTally(host, port, getLedgerBalanceCollectionRequest(firm, null));
            Map<String, Map<String, Double>> collectionDetails = parseCollectionLedgerDetails(collectionXml);
            for (Map.Entry<String, Map<String, Double>> entry : collectionDetails.entrySet()) {
                ledgerBalances.put(entry.getKey(), entry.getValue().getOrDefault("closing", 0.0));
            }
        }

        List<Map<String, Object>> ledgers = new ArrayList<>();
        for (String ledgerName : allLedgers.keySet()) {
            double closing = ledgerBalances.getOrDefault(ledgerName, 0.0);
            Map<String, Object> ledger = new LinkedHashMap<>();
            ledger.put("name", ledgerName);
            ledger.put("group", allLedgers.get(ledgerName));
            ledger.put("opening", 0.0);
            ledger.put("debit", 0.0);
            ledger.put("credit", 0.0);
            ledger.put("closing", closing);
            ledger.put("status", Math.abs(closing) < 0.01 ? "closed" : "open");
            ledger.put("firm", safe(firm));
            ledgers.add(ledger);
        }
        return ledgers;
    }

    public List<Map<String, Object>> fetchLedgersWithDate(String host, String port, String firm, String asOnDate) throws Exception {
        Map<String, String> allLedgers = fetchAllLedgers(host, port, firm);

        String detailedXml = fetchFromTally(host, port, getTrialBalanceRequest(firm, asOnDate));
        Map<String, Map<String, Double>> ledgerDetails = parseDetailedLedgers(detailedXml);
        if (isDetailsEmpty(ledgerDetails)) {
            String collectionXml = fetchFromTally(host, port, getLedgerBalanceCollectionRequest(firm, asOnDate));
            ledgerDetails = parseCollectionLedgerDetails(collectionXml);
        }
        if (ledgerDetails.isEmpty()) {
            String balanceXml = fetchFromTally(host, port, getTrialBalanceRequest(firm, null));
            Map<String, Double> ledgerBalances = parseLedgerBalances(balanceXml);
            for (String ledgerName : allLedgers.keySet()) {
                Map<String, Double> details = new LinkedHashMap<>();
                details.put("opening", 0.0);
                details.put("debit", 0.0);
                details.put("credit", 0.0);
                details.put("closing", ledgerBalances.getOrDefault(ledgerName, 0.0));
                ledgerDetails.put(ledgerName, details);
            }
        }

        List<Map<String, Object>> ledgers = new ArrayList<>();
        for (String ledgerName : allLedgers.keySet()) {
            Map<String, Double> details = ledgerDetails.getOrDefault(ledgerName, new LinkedHashMap<>());
            double opening = details.getOrDefault("opening", 0.0);
            double debit = details.getOrDefault("debit", 0.0);
            double credit = details.getOrDefault("credit", 0.0);
            double closing = details.getOrDefault("closing", 0.0);

            Map<String, Object> ledger = new LinkedHashMap<>();
            ledger.put("name", ledgerName);
            ledger.put("group", allLedgers.get(ledgerName));
            ledger.put("opening", opening);
            ledger.put("debit", debit);
            ledger.put("credit", credit);
            ledger.put("closing", closing);
            ledger.put("status", Math.abs(closing) < 0.01 ? "closed" : "open");
            ledger.put("firm", safe(firm));
            ledgers.add(ledger);
        }
        return ledgers;
    }

    public Map<String, Object> debugBalances(String host, String port, String firm, String asOnDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", safe(host).isEmpty() ? "localhost" : safe(host));
        result.put("port", safe(port).isEmpty() ? "9000" : safe(port));
        result.put("firm", safe(firm));
        result.put("date", safe(asOnDate));

        try {
            String trialXml = fetchFromTally(host, port, getTrialBalanceRequest(firm, asOnDate));
            String collectionXml = fetchFromTally(host, port, getLedgerBalanceCollectionRequest(firm, asOnDate));
            Map<String, Map<String, Double>> trialDetails = parseDetailedLedgers(trialXml);
            Map<String, Map<String, Double>> collectionDetails = parseCollectionLedgerDetails(collectionXml);

            result.put("trialDetailCount", trialDetails.size());
            result.put("collectionDetailCount", collectionDetails.size());
            result.put("trialPreview", previewXml(trialXml));
            result.put("collectionPreview", previewXml(collectionXml));
            result.put("message", "Balance debug data captured.");
        } catch (Exception e) {
            result.put("message", "Unable to capture balance debug data: " + e.getMessage());
        }

        return result;
    }

    private Map<String, String> fetchAllLedgers(String host, String port, String firm) throws Exception {
        List<String> requests = List.of(
                getMastersRequest(firm),
                getMastersFallbackRequest(firm),
                getLedgerCollectionRequest(firm),
                getLedgerCollectionExportDataRequest(firm)
        );

        Map<String, String> ledgers = new LinkedHashMap<>();
        for (String requestXml : requests) {
            String response = fetchFromTally(host, port, requestXml);
            ledgers.putAll(parseAllLedgers(response));
            if (!ledgers.isEmpty()) {
                return ledgers;
            }
        }

        if (!safe(firm).isEmpty()) {
            List<String> noFirmRequests = List.of(
                    getMastersRequest(""),
                    getMastersFallbackRequest(""),
                    getLedgerCollectionRequest(""),
                    getLedgerCollectionExportDataRequest("")
            );
            for (String requestXml : noFirmRequests) {
                String response = fetchFromTally(host, port, requestXml);
                ledgers.putAll(parseAllLedgers(response));
                if (!ledgers.isEmpty()) {
                    return ledgers;
                }
            }
        }

        return ledgers;
    }

    public List<String> fetchCompanies(String host, String port) throws Exception {
        TreeSet<String> companies = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        List<String> requests = List.of(
                getCompaniesRequest(),
                getCompaniesExportDataRequest(),
                getCompaniesCollectionRequest()
        );

        for (String requestXml : requests) {
            String response = fetchFromTally(host, port, requestXml);
            companies.addAll(parseCompanies(response));
            if (!companies.isEmpty()) {
                break;
            }
        }

        if (companies.isEmpty()) {
            try {
                // Fallback: if a company is already open in Tally, some reports expose the current company name.
                String mastersResponse = fetchFromTally(host, port, getMastersRequest(""));
                companies.addAll(parseCompanies(mastersResponse));
            } catch (Exception ignored) {
            }
        }

        return new ArrayList<>(companies);
    }

    private List<String> parseCompanies(String response) throws Exception {
        TreeSet<String> companies = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Document doc = parseXml(response);

        NodeList companyNodes = doc.getElementsByTagName("COMPANY");
        for (int i = 0; i < companyNodes.getLength(); i++) {
            Element company = (Element) companyNodes.item(i);
            addIfPresent(companies, company.getAttribute("NAME"));
            addIfPresent(companies, getElementValue(company, "NAME"));
            addIfPresent(companies, getElementValue(company, "REMOTECMPNAME"));
            addIfPresent(companies, getElementValue(company, "CMPNAME"));
        }

        NodeList nameNodes = doc.getElementsByTagName("NAME");
        for (int i = 0; i < nameNodes.getLength(); i++) {
            Node node = nameNodes.item(i);
            String value = node.getTextContent();
            if (value != null && !value.trim().isEmpty() && !value.contains("\n")) {
                addIfPresent(companies, value);
            }
        }

        NodeList currentCompanyNodes = doc.getElementsByTagName("SVCURRENTCOMPANY");
        for (int i = 0; i < currentCompanyNodes.getLength(); i++) {
            addIfPresent(companies, currentCompanyNodes.item(i).getTextContent());
        }

        NodeList cmpNameNodes = doc.getElementsByTagName("CMPNAME");
        for (int i = 0; i < cmpNameNodes.getLength(); i++) {
            addIfPresent(companies, cmpNameNodes.item(i).getTextContent());
        }

        NodeList remoteCmpNodes = doc.getElementsByTagName("REMOTECMPNAME");
        for (int i = 0; i < remoteCmpNodes.getLength(); i++) {
            addIfPresent(companies, remoteCmpNodes.item(i).getTextContent());
        }

        return new ArrayList<>(companies);
    }

    public Map<String, Object> testConnection(String host, String port) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", safe(host).isEmpty() ? "localhost" : safe(host));
        result.put("port", safe(port).isEmpty() ? "9000" : safe(port));
        result.put("reachable", false);
        result.put("firmCount", 0);
        result.put("firms", new ArrayList<>());

        try {
            List<String> firms = fetchCompanies(host, port);
            result.put("reachable", true);
            result.put("firmCount", firms.size());
            result.put("firms", firms);
            result.put("message", firms.isEmpty()
                    ? "Connected to Tally, but no running/open firms were returned."
                    : "Connected to Tally successfully.");
        } catch (Exception e) {
            result.put("message", "Unable to connect to Tally: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Object> launchTally(String tallyPath) {
        Map<String, Object> result = new LinkedHashMap<>();
        String pathValue = safe(tallyPath);
        result.put("path", pathValue);

        if (pathValue.isEmpty()) {
            result.put("launched", false);
            result.put("message", "Tally path is empty.");
            return result;
        }

        Path path = Path.of(pathValue);
        if (!Files.exists(path)) {
            result.put("launched", false);
            result.put("message", "Tally executable not found at: " + pathValue);
            return result;
        }

        try {
            new ProcessBuilder(pathValue).start();
            result.put("launched", true);
            result.put("message", "Tally launch command sent successfully.");
        } catch (Exception e) {
            result.put("launched", false);
            result.put("message", "Unable to launch Tally: " + e.getMessage());
        }
        return result;
    }

    private String fetchFromTally(String host, String port, String xmlRequest) throws Exception {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildTallyUrl(host, port)))
                .header("Content-Type", "text/xml; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(xmlRequest))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String buildTallyUrl(String host, String port) {
        String cleanHost = safe(host).isEmpty() ? "localhost" : safe(host);
        String cleanPort = safe(port).isEmpty() ? "9000" : safe(port);
        if (cleanHost.startsWith("http://") || cleanHost.startsWith("https://")) {
            return cleanHost + (cleanHost.contains(":") && cleanHost.matches("^https?://.+:\\d+$") ? "" : ":" + cleanPort);
        }
        return "http://" + cleanHost + ":" + cleanPort;
    }

    private String getCompaniesRequest() {
        return "<ENVELOPE>"
                + "<HEADER><VERSION>1</VERSION><TALLYREQUEST>EXPORT</TALLYREQUEST></HEADER>"
                + "<BODY><DESC><REPORTNAME>List of Companies</REPORTNAME><STATICVARIABLES><SVEXPORTFORMAT>XML</SVEXPORTFORMAT></STATICVARIABLES></DESC></BODY>"
                + "</ENVELOPE>";
    }

    private String getCompaniesExportDataRequest() {
        return "<ENVELOPE>"
                + "<HEADER><VERSION>1</VERSION><TALLYREQUEST>EXPORT DATA</TALLYREQUEST></HEADER>"
                + "<BODY><EXPORTDATA><REQUESTDESC><REPORTNAME>List of Companies</REPORTNAME>"
                + "<STATICVARIABLES><SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT></STATICVARIABLES>"
                + "</REQUESTDESC></EXPORTDATA></BODY>"
                + "</ENVELOPE>";
    }

    private String getCompaniesCollectionRequest() {
        return "<ENVELOPE>"
                + "<HEADER><VERSION>1</VERSION><TALLYREQUEST>EXPORT</TALLYREQUEST><TYPE>COLLECTION</TYPE><ID>Company Collection</ID></HEADER>"
                + "<BODY><DESC><STATICVARIABLES><SVEXPORTFORMAT>XML</SVEXPORTFORMAT></STATICVARIABLES>"
                + "<TDL><TDLMESSAGE>"
                + "<COLLECTION NAME=\"Company Collection\" ISINITIALIZE=\"Yes\">"
                + "<TYPE>Company</TYPE>"
                + "<FETCH>Name</FETCH>"
                + "<FETCH>CompanyName</FETCH>"
                + "</COLLECTION>"
                + "</TDLMESSAGE></TDL></DESC></BODY>"
                + "</ENVELOPE>";
    }

    private String getMastersRequest(String firm) {
        return "<ENVELOPE>"
                + "<HEADER><VERSION>1</VERSION><TALLYREQUEST>EXPORT</TALLYREQUEST></HEADER>"
                + "<BODY><DESC><REPORTNAME>List of Accounts</REPORTNAME><STATICVARIABLES>"
                + companyVariable(firm)
                + "<SVEXPORTFORMAT>XML</SVEXPORTFORMAT></STATICVARIABLES></DESC></BODY>"
                + "</ENVELOPE>";
    }

    private String getMastersFallbackRequest(String firm) {
        return "<ENVELOPE>"
                + "<HEADER><VERSION>1</VERSION><TALLYREQUEST>EXPORT</TALLYREQUEST></HEADER>"
                + "<BODY><DESC><REPORTNAME>All Masters</REPORTNAME><STATICVARIABLES>"
                + companyVariable(firm)
                + "<SVEXPORTFORMAT>XML</SVEXPORTFORMAT></STATICVARIABLES></DESC></BODY>"
                + "</ENVELOPE>";
    }

    private String getLedgerCollectionRequest(String firm) {
        return "<ENVELOPE>"
                + "<HEADER><VERSION>1</VERSION><TALLYREQUEST>EXPORT</TALLYREQUEST><TYPE>COLLECTION</TYPE><ID>Ledger Collection</ID></HEADER>"
                + "<BODY><DESC><STATICVARIABLES>"
                + companyVariable(firm)
                + "<SVEXPORTFORMAT>XML</SVEXPORTFORMAT></STATICVARIABLES>"
                + "<TDL><TDLMESSAGE>"
                + "<COLLECTION NAME=\"Ledger Collection\" ISINITIALIZE=\"Yes\">"
                + "<TYPE>Ledger</TYPE>"
                + "<FETCH>Name</FETCH>"
                + "<FETCH>Parent</FETCH>"
                + "</COLLECTION>"
                + "</TDLMESSAGE></TDL></DESC></BODY>"
                + "</ENVELOPE>";
    }

    private String getLedgerCollectionExportDataRequest(String firm) {
        return "<ENVELOPE>"
                + "<HEADER><VERSION>1</VERSION><TALLYREQUEST>EXPORT DATA</TALLYREQUEST></HEADER>"
                + "<BODY><EXPORTDATA><REQUESTDESC>"
                + "<REPORTNAME>List of Accounts</REPORTNAME>"
                + "<STATICVARIABLES>"
                + companyVariable(firm)
                + "<SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>"
                + "</STATICVARIABLES>"
                + "</REQUESTDESC></EXPORTDATA></BODY>"
                + "</ENVELOPE>";
    }

    private String getTrialBalanceRequest(String firm, String asOnDate) {
        StringBuilder builder = new StringBuilder();
        builder.append("<ENVELOPE>")
                .append("<HEADER><VERSION>1</VERSION><TALLYREQUEST>EXPORT</TALLYREQUEST></HEADER>")
                .append("<BODY><DESC><REPORTNAME>Trial Balance</REPORTNAME><STATICVARIABLES>")
                .append(companyVariable(firm))
                .append("<SVEXPORTFORMAT>XML</SVEXPORTFORMAT>")
                .append("<EXPLODEALL>Yes</EXPLODEALL>");
        if (asOnDate != null && !asOnDate.isBlank()) {
            builder.append("<ASONCHOICE>2</ASONCHOICE>")
                    .append("<ASONDATESELECTION>").append(xmlEscape(asOnDate)).append("</ASONDATESELECTION>");
        }
        builder.append("</STATICVARIABLES></DESC></BODY></ENVELOPE>");
        return builder.toString();
    }

    private String getLedgerBalanceCollectionRequest(String firm, String asOnDate) {
        StringBuilder builder = new StringBuilder();
        builder.append("<ENVELOPE>")
                .append("<HEADER><VERSION>1</VERSION><TALLYREQUEST>EXPORT</TALLYREQUEST><TYPE>COLLECTION</TYPE><ID>Ledger Balance Collection</ID></HEADER>")
                .append("<BODY><DESC><STATICVARIABLES>")
                .append(companyVariable(firm))
                .append("<SVEXPORTFORMAT>XML</SVEXPORTFORMAT>");
        if (asOnDate != null && !asOnDate.isBlank()) {
            builder.append("<SVFROMDATE>").append(xmlEscape(asOnDate)).append("</SVFROMDATE>")
                    .append("<SVTODATE>").append(xmlEscape(asOnDate)).append("</SVTODATE>")
                    .append("<ASONCHOICE>2</ASONCHOICE>")
                    .append("<ASONDATESELECTION>").append(xmlEscape(asOnDate)).append("</ASONDATESELECTION>");
        }
        builder.append("</STATICVARIABLES><TDL><TDLMESSAGE>")
                .append("<COLLECTION NAME=\"Ledger Balance Collection\" ISINITIALIZE=\"Yes\">")
                .append("<TYPE>Ledger</TYPE>")
                .append("<FETCH>Name</FETCH>")
                .append("<FETCH>Parent</FETCH>")
                .append("<FETCH>OpeningBalance</FETCH>")
                .append("<FETCH>ClosingBalance</FETCH>")
                .append("<FETCH>DebitTotal</FETCH>")
                .append("<FETCH>CreditTotal</FETCH>")
                .append("</COLLECTION>")
                .append("</TDLMESSAGE></TDL></DESC></BODY></ENVELOPE>");
        return builder.toString();
    }

    private Map<String, String> parseAllLedgers(String xml) throws Exception {
        Map<String, String> ledgers = new LinkedHashMap<>();
        Document doc = parseXml(xml);
        NodeList ledgerNodes = doc.getElementsByTagName("LEDGER");
        for (int i = 0; i < ledgerNodes.getLength(); i++) {
            Element ledger = (Element) ledgerNodes.item(i);
            String name = getLedgerName(ledger);
            String parent = getLedgerParent(ledger);
            if (name != null && !name.isBlank()) {
                ledgers.put(name.trim(), parent == null || parent.isBlank() ? "Ungrouped" : parent.trim());
            }
        }

        if (!ledgers.isEmpty()) {
            return ledgers;
        }

        NodeList objects = doc.getElementsByTagName("COLLECTION");
        for (int i = 0; i < objects.getLength(); i++) {
            Element collection = (Element) objects.item(i);
            NodeList children = collection.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node node = children.item(j);
                if (!(node instanceof Element)) {
                    continue;
                }
                Element element = (Element) node;
                String tag = element.getTagName();
                if (!tag.toUpperCase().contains("LEDGER")) {
                    continue;
                }
                String name = getLedgerName(element);
                String parent = getLedgerParent(element);
                if (name != null && !name.isBlank()) {
                    ledgers.put(name.trim(), parent == null ? "Ungrouped" : parent.trim());
                }
            }
        }
        return ledgers;
    }

    private Map<String, Double> parseLedgerBalances(String xml) throws Exception {
        Map<String, Double> balances = new LinkedHashMap<>();
        Document doc = parseXml(xml);
        NodeList dspNames = doc.getElementsByTagName("DSPACCNAME");
        for (int i = 0; i < dspNames.getLength(); i++) {
            String name = normalizeLedgerKey(dspNames.item(i).getTextContent());
            String amountStr = firstNonBlank(
                    getTagTextAt(doc, "CLOSINGBALANCE", i),
                    getTagTextAt(doc, "CLBALANCE", i),
                    getTagTextAt(doc, "AMOUNT", i),
                    getTagTextAt(doc, "NETAMOUNT", i)
            );
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            try {
                balances.put(name.trim(), Double.parseDouble(amountStr.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return balances;
    }

    private Map<String, Map<String, Double>> parseDetailedLedgers(String xml) throws Exception {
        Map<String, Map<String, Double>> details = new LinkedHashMap<>();
        Document doc = parseXml(xml);
        NodeList ledgers = doc.getElementsByTagName("LEDGER");
        for (int i = 0; i < ledgers.getLength(); i++) {
            Element ledger = (Element) ledgers.item(i);
            String name = getLedgerName(ledger);
            if (name == null || name.isBlank()) {
                continue;
            }

            Map<String, Double> ledgerDetails = new LinkedHashMap<>();
            ledgerDetails.put("opening", parseAmount(firstNonBlank(
                    getDirectChildValue(ledger, "OPENINGBALANCE"),
                    getDirectChildValue(ledger, "OPENINGAMOUNT"),
                    getElementValue(ledger, "OPENINGBALANCE"),
                    getElementValue(ledger, "OPENINGAMOUNT")
            )));
            ledgerDetails.put("debit", parseAmount(firstNonBlank(
                    getDirectChildValue(ledger, "DEBITAMOUNT"),
                    getDirectChildValue(ledger, "DEBITTOTAL"),
                    getDirectChildValue(ledger, "NETDEBIT"),
                    getElementValue(ledger, "DEBITAMOUNT"),
                    getElementValue(ledger, "DEBITTOTAL"),
                    getElementValue(ledger, "NETDEBIT")
            )));
            ledgerDetails.put("credit", parseAmount(firstNonBlank(
                    getDirectChildValue(ledger, "CREDITAMOUNT"),
                    getDirectChildValue(ledger, "CREDITTOTAL"),
                    getDirectChildValue(ledger, "NETCREDIT"),
                    getElementValue(ledger, "CREDITAMOUNT"),
                    getElementValue(ledger, "CREDITTOTAL"),
                    getElementValue(ledger, "NETCREDIT")
            )));
            ledgerDetails.put("closing", parseAmount(firstNonBlank(
                    getDirectChildValue(ledger, "CLOSINGBALANCE"),
                    getDirectChildValue(ledger, "CLBALANCE"),
                    getElementValue(ledger, "CLOSINGBALANCE"),
                    getElementValue(ledger, "CLBALANCE")
            )));
            details.put(name.trim(), ledgerDetails);
        }

        // Fallback for Tally trial balance exports that return row-based values instead of nested LEDGER totals.
        NodeList dspNames = doc.getElementsByTagName("DSPACCNAME");
        for (int i = 0; i < dspNames.getLength(); i++) {
            String name = normalizeLedgerKey(dspNames.item(i).getTextContent());
            if (name == null || name.isBlank()) {
                continue;
            }

            Map<String, Double> ledgerDetails = details.getOrDefault(name, new LinkedHashMap<>());
            ledgerDetails.put("opening", chooseAmount(ledgerDetails.get("opening"), parseAmount(firstNonBlank(
                    getTagTextAt(doc, "OPENINGBALANCE", i),
                    getTagTextAt(doc, "OPENINGAMOUNT", i)
            ))));
            ledgerDetails.put("debit", chooseAmount(ledgerDetails.get("debit"), parseAmount(firstNonBlank(
                    getTagTextAt(doc, "DEBITAMOUNT", i),
                    getTagTextAt(doc, "DEBITTOTAL", i),
                    getTagTextAt(doc, "NETDEBIT", i)
            ))));
            ledgerDetails.put("credit", chooseAmount(ledgerDetails.get("credit"), parseAmount(firstNonBlank(
                    getTagTextAt(doc, "CREDITAMOUNT", i),
                    getTagTextAt(doc, "CREDITTOTAL", i),
                    getTagTextAt(doc, "NETCREDIT", i)
            ))));
            ledgerDetails.put("closing", chooseAmount(ledgerDetails.get("closing"), parseAmount(firstNonBlank(
                    getTagTextAt(doc, "CLOSINGBALANCE", i),
                    getTagTextAt(doc, "CLBALANCE", i),
                    getTagTextAt(doc, "AMOUNT", i),
                    getTagTextAt(doc, "NETAMOUNT", i)
            ))));
            details.put(name, ledgerDetails);
        }
        return details;
    }

    private Map<String, Map<String, Double>> parseCollectionLedgerDetails(String xml) throws Exception {
        Map<String, Map<String, Double>> details = new LinkedHashMap<>();
        Document doc = parseXml(xml);
        NodeList collections = doc.getElementsByTagName("COLLECTION");
        for (int i = 0; i < collections.getLength(); i++) {
            Element collection = (Element) collections.item(i);
            NodeList children = collection.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node node = children.item(j);
                if (!(node instanceof Element)) {
                    continue;
                }
                Element element = (Element) node;
                String name = getLedgerName(element);
                if (name == null || name.isBlank()) {
                    continue;
                }

                Map<String, Double> ledgerDetails = new LinkedHashMap<>();
                ledgerDetails.put("opening", parseAmount(firstNonBlank(
                        getDirectChildValue(element, "OPENINGBALANCE"),
                        getDirectChildValue(element, "OPENINGAMOUNT"),
                        getElementValue(element, "OPENINGBALANCE"),
                        getElementValue(element, "OPENINGAMOUNT")
                )));
                ledgerDetails.put("debit", parseAmount(firstNonBlank(
                        getDirectChildValue(element, "DEBITTOTAL"),
                        getDirectChildValue(element, "DEBITAMOUNT"),
                        getElementValue(element, "DEBITTOTAL"),
                        getElementValue(element, "DEBITAMOUNT")
                )));
                ledgerDetails.put("credit", parseAmount(firstNonBlank(
                        getDirectChildValue(element, "CREDITTOTAL"),
                        getDirectChildValue(element, "CREDITAMOUNT"),
                        getElementValue(element, "CREDITTOTAL"),
                        getElementValue(element, "CREDITAMOUNT")
                )));
                ledgerDetails.put("closing", parseAmount(firstNonBlank(
                        getDirectChildValue(element, "CLOSINGBALANCE"),
                        getDirectChildValue(element, "CLBALANCE"),
                        getElementValue(element, "CLOSINGBALANCE"),
                        getElementValue(element, "CLBALANCE")
                )));
                details.put(normalizeLedgerKey(name), ledgerDetails);
            }
        }
        return details;
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(sanitizeXml(xml).getBytes()));
    }

    private String getElementValue(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private String getTagTextAt(Document doc, String tagName, int index) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (index >= 0 && index < nodes.getLength()) {
            return nodes.item(index).getTextContent();
        }
        return null;
    }

    private String getDirectChildValue(Element element, String tagName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && tagName.equalsIgnoreCase(((Element) node).getTagName())) {
                return node.getTextContent();
            }
        }
        return null;
    }

    private String getAttributeValue(Element element, String attributeName) {
        if (element.hasAttribute(attributeName)) {
            return element.getAttribute(attributeName);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element child = (Element) node;
                if (child.hasAttribute(attributeName)) {
                    return child.getAttribute(attributeName);
                }
            }
        }
        return null;
    }

    private String getLedgerName(Element element) {
        return firstNonBlank(
                getAttributeValue(element, "NAME"),
                getDirectChildValue(element, "NAME"),
                getDirectChildValue(element, "LEDGERNAME"),
                getElementValue(element, "NAME")
        );
    }

    private String getLedgerParent(Element element) {
        return firstNonBlank(
                getAttributeValue(element, "PARENT"),
                getDirectChildValue(element, "PARENT"),
                getDirectChildValue(element, "MAINGROUP"),
                getElementValue(element, "PARENT"),
                getElementValue(element, "MAINGROUP")
        );
    }

    private String companyVariable(String firm) {
        return safe(firm).isEmpty() ? "" : "<SVCURRENTCOMPANY>" + xmlEscape(firm) + "</SVCURRENTCOMPANY>";
    }

    private double parseAmount(String value) {
        try {
            return value == null || value.isBlank() ? 0.0 : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double chooseAmount(Double primary, double fallback) {
        double primaryValue = primary == null ? 0.0 : primary;
        if (Math.abs(primaryValue) > 0.0001) {
            return primaryValue;
        }
        return fallback;
    }

    private String normalizeLedgerKey(String value) {
        return safe(value)
                .replaceAll("\\s+", " ")
                .replace("\u0004", "")
                .trim();
    }

    private void addIfPresent(TreeSet<String> companies, String value) {
        String text = safe(value);
        if (!text.isEmpty()) {
            companies.add(text);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String xmlEscape(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String sanitizeXml(String xml) {
        if (xml == null) {
            return "";
        }

        String sanitized = xml;

        // Remove invalid numeric character references such as &#4; that Tally may emit.
        sanitized = sanitized.replaceAll("&#(?:0*[0-8]|0*11|0*12|0*14|0*15|0*16|0*17|0*18|0*19|0*20|0*21|0*22|0*23|0*24|0*25|0*26|0*27|0*28|0*29|0*30|0*31);", "");
        sanitized = sanitized.replaceAll("&#x(?:0*[0-8]|0*B|0*C|0*E|0*F|0*1[0-9A-F]);", "");

        StringBuilder builder = new StringBuilder(sanitized.length());
        for (int i = 0; i < sanitized.length(); i++) {
            char ch = sanitized.charAt(i);
            if (ch == 0x9 || ch == 0xA || ch == 0xD || ch >= 0x20) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private boolean isDetailsEmpty(Map<String, Map<String, Double>> details) {
        if (details == null || details.isEmpty()) {
            return true;
        }
        for (Map<String, Double> entry : details.values()) {
            if (Math.abs(entry.getOrDefault("opening", 0.0)) > 0.0001
                    || Math.abs(entry.getOrDefault("debit", 0.0)) > 0.0001
                    || Math.abs(entry.getOrDefault("credit", 0.0)) > 0.0001
                    || Math.abs(entry.getOrDefault("closing", 0.0)) > 0.0001) {
                return false;
            }
        }
        return true;
    }

    private String previewXml(String xml) {
        return sanitizeXml(xml).replaceAll("\\s+", " ").trim().substring(0, Math.min(800, sanitizeXml(xml).replaceAll("\\s+", " ").trim().length()));
    }
}
