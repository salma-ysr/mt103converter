package com.attijari.MT103converter.converters;

import com.attijari.MT103converter.models.Pacs008ToMT103Conversion;
import com.attijari.MT103converter.services.Validator;
import com.attijari.MT103converter.models.ErrorCall;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversion PACS008 -> MT103 (logique inverse enrichie)
 */
@Component
public class Pacs008ToMT103Converter {
    private static final Logger logger = LogManager.getLogger(Pacs008ToMT103Converter.class);
    private static final String NS = "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08";

    @Autowired(required = false)
    private Validator validator; // facultatif (tests unitaires peuvent bypass)

    public ConversionResult process(String xml) {
        if (xml == null || xml.isBlank()) {
            return new ConversionResult(false, null, "Le contenu XML pacs.008 est vide.");
        }
        try {
            // 1. Validation XSD (si validator disponible)
            if (validator != null) {
                ErrorCall validationErrors = validator.validatePacs008(xml);
                if (validationErrors.hasErrors()) {
                    StringBuilder sb = new StringBuilder("Validation XSD échouée:\n");
                    validationErrors.getErrors().forEach(err -> sb.append("• ").append(err).append('\n'));
                    return new ConversionResult(false, null, sb.toString().trim());
                }
            }

            // 2. Parse DOM
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // Rechercher le noeud CdtTrfTxInf (obligatoire pour la conversion)
            NodeList txList = doc.getElementsByTagNameNS(NS, "CdtTrfTxInf");
            if (txList.getLength() == 0) {
                return new ConversionResult(false, null, "Élément CdtTrfTxInf introuvable dans le pacs.008");
            }
            Element tx = (Element) txList.item(0);

            // 3. Extraire champs nécessaires
            String instrId = text(tx, "PmtId/InstrId");
            if (instrId == null || instrId.isBlank()) instrId = text(tx, "PmtId/EndToEndId");
            if (instrId == null || instrId.isBlank()) instrId = "REF" + System.currentTimeMillis();

            Element amtEl = firstChild(tx, "IntrBkSttlmAmt");
            String amount = amtEl != null ? amtEl.getTextContent() : null;
            String currency = amtEl != null ? amtEl.getAttribute("Ccy") : null;

            String settlementDate = text(tx, "IntrBkSttlmDt");
            if (settlementDate == null) {
                // tenter dans GrpHdr si manquant
                settlementDate = text(doc.getDocumentElement(), "GrpHdr/CreDtTm");
            }

            String chrgBr = text(tx, "ChrgBr");
            String svcLvl = text(tx, "PmtTpInf/SvcLvl/Cd");

            // Debtor
            String debtorName = text(tx, "Dbtr/Nm");
            List<String> debtorAdr = adrLines(tx, "Dbtr/PstlAdr/AdrLine");
            String debtorAcct = text(tx, "DbtrAcct/Id/Othr/Id");

            // Creditor
            String creditorName = text(tx, "Cdtr/Nm");
            List<String> creditorAdr = adrLines(tx, "Cdtr/PstlAdr/AdrLine");
            String creditorAcct = text(tx, "CdtrAcct/Id/Othr/Id");

            // Remittance
            String remittance = text(tx, "RmtInf/Ustrd");

            // 4. Mapping inverse
            String field23B = mapServiceLevelReverse(svcLvl);
            String field71A = mapChargeBearerReverse(chrgBr);
            String field32A = build32A(settlementDate, currency, amount);

            String field50K = buildPartyField(debtorAcct, debtorName, debtorAdr);
            String field59 = buildPartyField(creditorAcct, creditorName, creditorAdr);

            // 5. Construire MT103
            StringBuilder mt = new StringBuilder();
            mt.append("{1:F01BANKDEFAXXX0000000000}{2:O103").append(nowDateYYMMDD())
              .append("BANKDEFAXXXBANKFRPPXXX0000000000}{4:\n");
            mt.append(tagLine("20", instrId));
            mt.append(tagLine("23B", field23B));
            mt.append(tagLine("32A", field32A));
            mt.append(tagLine("33B", build33B(currency, amount)));
            mt.append(tagLine("50K", field50K));
            mt.append(tagLine("59", field59));
            if (remittance != null && !remittance.isBlank()) {
                mt.append(tagLine("70", sanitize(remittance, 140))); // Tag 70 multi-ligne simplifié
            }
            mt.append(":").append("71A").append(":").append(field71A == null ? "" : field71A).append("\n");
            // Clôturer correctement le bloc 4 avec "-}" avant d'ouvrir le bloc 5
            mt.append("-}\n{5:{CHK:000000000000}}\n");

            return new ConversionResult(true, mt.toString(), null);
        } catch (Exception e) {
            logger.error("Erreur conversion inverse: {}", e.getMessage(), e);
            return new ConversionResult(false, null, "Erreur lors du parsing XML: " + e.getMessage());
        }
    }

    // ================= Utility methods =================
    private String tagLine(String tag, String value) {
        return ":" + tag + ":" + (value == null ? "" : value) + "\n";
    }

    private String sanitize(String val, int max) {
        if (val == null) return "";
        String cleaned = val.replaceAll("\r", "").trim();
        if (cleaned.length() > max) return cleaned.substring(0, max);
        return cleaned;
    }

    private String build33B(String currency, String amount) {
        String c = (currency == null || currency.isBlank()) ? "EUR" : currency.trim();
        String a = "0,00";
        if (amount != null && !amount.isBlank()) {
            a = amount.replace('.', ',');
        }
        return c + a;
    }

    private String buildPartyField(String account, String name, List<String> adrLines) {
        StringBuilder sb = new StringBuilder();
        if (account != null && !account.isBlank()) {
            sb.append('/').append(account.trim()).append('\n');
        }
        if (name != null && !name.isBlank()) {
            sb.append(sanitize(name, 35)).append('\n');
        } else {
            sb.append("UNKNOWN").append('\n');
        }
        for (String l : adrLines) {
            if (l == null || l.isBlank()) continue;
            sb.append(sanitize(l, 35)).append('\n');
        }
        // Remove trailing newline
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private List<String> adrLines(Element root, String path) {
        List<String> lines = new ArrayList<>();
        String[] parts = path.split("/");
        // Navigate down ignoring namespace convenience: collect final element name
        String last = parts[parts.length - 1];
        NodeList nl = root.getElementsByTagNameNS(NS, last);
        for (int i = 0; i < nl.getLength(); i++) {
            lines.add(nl.item(i).getTextContent());
        }
        return lines;
    }

    private Element firstChild(Element parent, String localName) {
        NodeList nl = parent.getElementsByTagNameNS(NS, localName);
        if (nl.getLength() > 0) return (Element) nl.item(0);
        return null;
    }

    private String text(Node context, String path) {
        if (context == null || path == null) return null;
        String[] parts = path.split("/");
        return findRecursive(context, parts, 0);
    }

    private String findRecursive(Node node, String[] parts, int idx) {
        if (idx >= parts.length) return null;
        String target = parts[idx];
        NodeList children;
        if (node instanceof Document doc) {
            children = doc.getDocumentElement().getChildNodes();
        } else {
            children = node.getChildNodes();
        }
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                if (c.getLocalName() != null && c.getLocalName().equals(target)) {
                    if (idx == parts.length - 1) {
                        return c.getTextContent();
                    }
                    return findRecursive(c, parts, idx + 1);
                }
            }
        }
        // Fallback global search by localName
        if (node instanceof Element || node instanceof Document) {
            NodeList nl = (node instanceof Document d ? d : node.getOwnerDocument()).getElementsByTagNameNS(NS, target);
            if (nl.getLength() > 0) {
                Node c = nl.item(0);
                if (idx == parts.length - 1) return c.getTextContent();
                return findRecursive(c, parts, idx + 1);
            }
        }
        return null;
    }

    private String mapServiceLevelReverse(String svcLvl) {
        if (svcLvl == null) return "CRED";
        switch (svcLvl.toUpperCase()) {
            case "NORM": return "CRED";
            case "NURG": return "CRTS";
            default: return "CRED";
        }
    }

    private String mapChargeBearerReverse(String chrgBr) {
        if (chrgBr == null) return "SHA";
        switch (chrgBr.toUpperCase()) {
            case "DEBT": return "OUR";
            case "CRED": return "BEN";
            case "SHAR": return "SHA";
            default: return "SHA";
        }
    }

    private String build32A(String isoDate, String currency, String amount) {
        String date = "000000";
        try {
            if (isoDate != null && !isoDate.isBlank()) {
                // Accept either yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss formats
                String datePart = isoDate.contains("T") ? isoDate.substring(0, isoDate.indexOf('T')) : isoDate;
                LocalDate d = LocalDate.parse(datePart);
                date = d.format(DateTimeFormatter.ofPattern("yyMMdd"));
            }
        } catch (Exception ignored) {}
        if (currency == null || currency.isBlank()) currency = "EUR";
        String amt = "0,00";
        if (amount != null && !amount.isBlank()) {
            amt = amount.replace('.', ',');
        }
        return date + currency + amt;
    }

    private String nowDateYYMMDD() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
    }

    public static class ConversionResult {
        private final boolean success;
        private final String mt103Content;
        private final String errorMessage;
        public ConversionResult(boolean success, String mt103Content, String errorMessage) {
            this.success = success; this.mt103Content = mt103Content; this.errorMessage = errorMessage; }
        public boolean isSuccess() { return success; }
        public String getMt103Content() { return mt103Content; }
        public String getErrorMessage() { return errorMessage; }
    }
}
