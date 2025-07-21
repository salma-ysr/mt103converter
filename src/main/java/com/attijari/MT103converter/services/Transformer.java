package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.Pacs008Msg;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * transformer un message MT103 en message pacs.008
 */
@Service
public class Transformer {

    /**
     * Transforme MT103Msg en Pacs008Msg.
     *
     * @param mt103 message brut/initial
     * @return message transformé
     */
    public Pacs008Msg transform(MT103Msg mt103) {
        if (mt103 == null) {
            return null;
        }

        String xml = generatePacs008Xml(mt103);
        return new Pacs008Msg(xml);
    }

    private String generatePacs008Xml(MT103Msg mt103) {
        StringBuilder xml = new StringBuilder();

        // En-tête XML
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">\n");
        xml.append("  <FIToFICustomerCreditTransfer>\n");

        // GroupHeader (obligatoire)
        xml.append("    <GrpHdr>\n");
        xml.append("      <MsgId>").append(generateMessageId()).append("</MsgId>\n");
        xml.append("      <CreDtTm>").append(getCurrentDateTime()).append("</CreDtTm>\n");
        xml.append("      <NbOfTxs>1</NbOfTxs>\n");
        xml.append("      <CtrlSum>").append(extractAmount(mt103.getField("32A"))).append("</CtrlSum>\n");
        xml.append("      <InstgAgt>\n");
        xml.append("        <FinInstnId>\n");
        xml.append("          <BICFI>").append(extractSenderBIC(mt103)).append("</BICFI>\n");
        xml.append("        </FinInstnId>\n");
        xml.append("      </InstgAgt>\n");
        xml.append("      <InstdAgt>\n");
        xml.append("        <FinInstnId>\n");
        xml.append("          <BICFI>").append(extractReceiverBIC(mt103)).append("</BICFI>\n");
        xml.append("        </FinInstnId>\n");
        xml.append("      </InstdAgt>\n");
        xml.append("    </GrpHdr>\n");

        // CreditTransferTransactionInformation
        xml.append("    <CdtTrfTxInf>\n");
        xml.append("      <PmtId>\n");
        xml.append("        <InstrId>").append(mt103.getField("20")).append("</InstrId>\n");
        xml.append("        <EndToEndId>").append(mt103.getField("20")).append("</EndToEndId>\n");
        xml.append("      </PmtId>\n");

        // Payment Type Information
        xml.append("      <PmtTpInf>\n");
        xml.append("        <SvcLvl>\n");
        xml.append("          <Cd>").append(mapServiceLevel(mt103.getField("23B"))).append("</Cd>\n");
        xml.append("        </SvcLvl>\n");
        xml.append("      </PmtTpInf>\n");

        // Amount
        xml.append("      <Amt>\n");
        xml.append("        <InstdAmt Ccy=\"").append(extractCurrency(mt103.getField("32A"))).append("\">");
        xml.append(extractAmount(mt103.getField("32A"))).append("</InstdAmt>\n");
        xml.append("      </Amt>\n");

        // Charges Bearer
        xml.append("      <ChrgBr>").append(mapChargeBearer(mt103.getField("71A"))).append("</ChrgBr>\n");

        // Debtor (Field 50A/50K)
        xml.append("      <Dbtr>\n");
        String debtor = !mt103.getField("50A").isEmpty() ? mt103.getField("50A") : mt103.getField("50K");
        xml.append("        <Nm>").append(extractName(debtor)).append("</Nm>\n");
        if (hasAddress(debtor)) {
            xml.append("        <PstlAdr>\n");
            xml.append("          <AdrLine>").append(extractAddress(debtor)).append("</AdrLine>\n");
            xml.append("        </PstlAdr>\n");
        }
        xml.append("      </Dbtr>\n");

        // Debtor Account (si disponible dans 50A)
        if (!mt103.getField("50A").isEmpty() && hasAccount(mt103.getField("50A"))) {
            xml.append("      <DbtrAcct>\n");
            xml.append("        <Id>\n");
            xml.append("          <Othr>\n");
            xml.append("            <Id>").append(extractAccount(mt103.getField("50A"))).append("</Id>\n");
            xml.append("          </Othr>\n");
            xml.append("        </Id>\n");
            xml.append("      </DbtrAcct>\n");
        }

        // Creditor (Field 59)
        xml.append("      <Cdtr>\n");
        String creditor = mt103.getField("59");
        xml.append("        <Nm>").append(extractName(creditor)).append("</Nm>\n");
        if (hasAddress(creditor)) {
            xml.append("        <PstlAdr>\n");
            xml.append("          <AdrLine>").append(extractAddress(creditor)).append("</AdrLine>\n");
            xml.append("        </PstlAdr>\n");
        }
        xml.append("      </Cdtr>\n");

        // Creditor Account (si disponible dans 59)
        if (hasAccount(creditor)) {
            xml.append("      <CdtrAcct>\n");
            xml.append("        <Id>\n");
            xml.append("          <Othr>\n");
            xml.append("            <Id>").append(extractAccount(creditor)).append("</Id>\n");
            xml.append("          </Othr>\n");
            xml.append("        </Id>\n");
            xml.append("      </CdtrAcct>\n");
        }

        // Remittance Information (Field 70 si disponible)
        String remittanceInfo = mt103.getField("70");
        if (!remittanceInfo.isEmpty()) {
            xml.append("      <RmtInf>\n");
            xml.append("        <Ustrd>").append(remittanceInfo).append("</Ustrd>\n");
            xml.append("      </RmtInf>\n");
        }

        xml.append("    </CdtTrfTxInf>\n");
        xml.append("  </FIToFICustomerCreditTransfer>\n");
        xml.append("</Document>");

        return xml.toString();
    }

    // Méthodes utilitaires pour extraire et mapper les données
    private String generateMessageId() {
        return "MSG" + System.currentTimeMillis();
    }

    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    private String extractAmount(String field32A) {
        if (field32A == null || field32A.isEmpty()) return "0.00";
        // Format 32A: YYMMDDCURRENCYAMOUNT
        return field32A.length() > 9 ? field32A.substring(9) : "0.00";
    }

    private String extractCurrency(String field32A) {
        if (field32A == null || field32A.isEmpty()) return "EUR";
        // Format 32A: YYMMDDCURRENCYAMOUNT
        return field32A.length() > 9 ? field32A.substring(6, 9) : "EUR";
    }

    private String extractSenderBIC(MT103Msg mt103) {
        // Dans un vrai environnement, cela viendrait du header du message
        return "BANKDEFAXXX"; // Exemple
    }

    private String extractReceiverBIC(MT103Msg mt103) {
        // Dans un vrai environnement, cela viendrait du header du message
        return "BANKFRPPXXX"; // Exemple
    }

    private String mapServiceLevel(String field23B) {
        if (field23B == null || field23B.isEmpty()) return "NORM";
        switch (field23B.toUpperCase()) {
            case "CRED": return "NORM";
            case "CRTS": return "NURG";
            default: return "NORM";
        }
    }

    private String mapChargeBearer(String field71A) {
        if (field71A == null || field71A.isEmpty()) return "SHAR";
        switch (field71A.toUpperCase()) {
            case "OUR": return "DEBT";
            case "BEN": return "CRED";
            case "SHA": return "SHAR";
            default: return "SHAR";
        }
    }

    private String extractName(String field) {
        if (field == null || field.isEmpty()) return "";
        String[] lines = field.split("\n");
        return lines.length > 0 ? lines[0].trim() : "";
    }

    private String extractAddress(String field) {
        if (field == null || field.isEmpty()) return "";
        String[] lines = field.split("\n");
        StringBuilder address = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            if (i > 1) address.append(" ");
            address.append(lines[i].trim());
        }
        return address.toString();
    }

    private boolean hasAddress(String field) {
        if (field == null || field.isEmpty()) return false;
        return field.split("\n").length > 1;
    }

    private boolean hasAccount(String field) {
        if (field == null || field.isEmpty()) return false;
        // Vérifie si le champ commence par un numéro de compte
        String firstLine = field.split("\n")[0];
        return firstLine.matches("^[0-9/]+.*");
    }

    private String extractAccount(String field) {
        if (field == null || field.isEmpty()) return "";
        String firstLine = field.split("\n")[0];
        // Extrait le numéro de compte de la première ligne
        String[] parts = firstLine.split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }
}
