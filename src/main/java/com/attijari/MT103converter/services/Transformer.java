package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.Pacs008Msg;
import org.springframework.stereotype.Service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.apache.tomcat.util.http.FastHttpDateFormat.getCurrentDate;

/**
 * transformer un message MT103 en message pacs.008
 */
@Service
public class Transformer {
    private static final Logger logger = LogManager.getLogger(Transformer.class);

    /**
     * Transforme MT103Msg en Pacs008Msg.
     *
     * @param mt103 message brut/initial
     * @return message transformé
     */
    public Pacs008Msg transform(MT103Msg mt103) {
        logger.debug("Transforming MT103Msg to Pacs008Msg");
        if (mt103 == null) {
            logger.error("MT103Msg is null, cannot transform");
            return null;
        }
        String xml = generatePacs008Xml(mt103);
        logger.info("Transformation complete. XML length: {}", xml.length());
        return new Pacs008Msg(xml);
    }

    private String generatePacs008Xml(MT103Msg mt103) {
        logger.trace("Generating PACS.008 XML from MT103Msg");
        StringBuilder xml = new StringBuilder();

        // En-tête XML
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">\n");
        xml.append("  <FIToFICstmrCdtTrf>\n");

        // GroupHeader (obligatoire)
        xml.append("    <GrpHdr>\n");
        xml.append("      <MsgId>").append(generateMessageId()).append("</MsgId>\n");
        xml.append("      <CreDtTm>").append(getCurrentDateTime()).append("</CreDtTm>\n");
        xml.append("      <NbOfTxs>1</NbOfTxs>\n");

        //settlement information obligatoire
        xml.append("    <SttlmInf>\n");
        xml.append("      <SttlmMtd>CLRG</SttlmMtd>\n");
        xml.append("      <ClrSys>\n");
        xml.append("        <Cd>RG</Cd>\n");
        xml.append("      </ClrSys>\n");
        xml.append("    </SttlmInf>\n");
        xml.append("    </GrpHdr>\n");

        /*
        xml.append("      <CtrlSum>").append(extractAmount(mt103.getField("32A"))).append("</CtrlSum>\n");
         */

        // CreditTransferTransactionInformation
        xml.append("    <CdtTrfTxInf>\n");

        //payment ID
        xml.append("      <PmtId>\n");
        String paymentId = mt103.getField("20");
        xml.append("        <InstrId>").append(paymentId).append("</InstrId>\n");
        xml.append("        <EndToEndId>").append(paymentId).append("</EndToEndId>\n");
        xml.append("        <UETR>").append(UUID.randomUUID().toString()).append("</UETR>\n");
        xml.append("      </PmtId>\n");

        // Payment Type Information
        xml.append("      <PmtTpInf>\n");
        xml.append("        <SvcLvl>\n");
        xml.append("          <Cd>").append(mapServiceLevel(mt103.getField("23B"))).append("</Cd>\n");
        xml.append("        </SvcLvl>\n");
        xml.append("      </PmtTpInf>\n");

        // Amount
        xml.append("      <IntrBkSttlmAmt Ccy=\"").append(extractCurrency(mt103.getField("32A"))).append("\">")
                .append(extractAmount(mt103.getField("32A"))).append("</IntrBkSttlmAmt>\n");

        // Date règlement interbancaire obligatoire
        xml.append("      <IntrBkSttlmDt>").append(getCurrentDate()).append("</IntrBkSttlmDt>\n");

        // Charges Bearer
        xml.append("      <ChrgBr>").append(mapChargeBearer(mt103.getField("71A"))).append("</ChrgBr>\n");

        // InstgAgt
        xml.append("      <InstgAgt>\n");
        xml.append("        <FinInstnId>\n");
        xml.append("          <BICFI>").append(extractSenderBIC(mt103)).append("</BICFI>\n");
        xml.append("        </FinInstnId>\n");
        xml.append("      </InstgAgt>\n");

        // InstdAgt
        xml.append("      <InstdAgt>\n");
        xml.append("        <FinInstnId>\n");
        xml.append("          <BICFI>").append(extractReceiverBIC(mt103)).append("</BICFI>\n");
        xml.append("        </FinInstnId>\n");
        xml.append("      </InstdAgt>\n");

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

        // Debtor Agent obligatoire
        xml.append("      <DbtrAgt>\n");
        xml.append("        <FinInstnId>\n");
        xml.append("          <BICFI>").append(extractSenderBIC(mt103)).append("</BICFI>\n");
        xml.append("        </FinInstnId>\n");
        xml.append("      </DbtrAgt>\n");

        // debitor agent account
        xml.append("      <DbtrAgtAcct>\n");
        xml.append("        <Id>\n");
        xml.append("          <Othr>\n");
        xml.append("            <Id>").append("AGT123456").append("</Id>\n"); // un ID fictif
        xml.append("          </Othr>\n");
        xml.append("        </Id>\n");
        xml.append("      </DbtrAgtAcct>\n");

        // Creditor Agent
        xml.append("      <CdtrAgt>\n");
        xml.append("        <FinInstnId>\n");
        xml.append("          <BICFI>").append(extractReceiverBIC(mt103)).append("</BICFI>\n");
        xml.append("        </FinInstnId>\n");
        xml.append("      </CdtrAgt>\n");

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
        xml.append("  </FIToFICstmrCdtTrf>\n");
        xml.append("</Document>");

        //test
        System.out.println("---- XML GENERATED ----");
        System.out.println(xml.toString());
        System.out.println("-----------------------");

        return xml.toString();
    }

    // Méthodes utilitaires pour extraire et mapper les données
    private String generateMessageId() {
        return "MSG" + System.currentTimeMillis();
    }

    private String getCurrentDateTime() {
        //return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"); //include décalage horaire
        String formatted = OffsetDateTime.now(ZoneOffset.systemDefault()).format(formatter);
        return formatted;
    }

    private String extractAmount(String field32A) {
        if (field32A == null || field32A.isEmpty()) return "0.00";
        String raw = field32A.length() > 9 ? field32A.substring(9) : "0.00";
        raw = raw.replace(",", ".");
        try {
            BigDecimal amount = new BigDecimal(raw);
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString(); //forcer un décimal valide
        } catch (NumberFormatException e) {
            return "0.00";
        }
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
        // enlever le "/"
        if (firstLine.startsWith("/")) {
            firstLine = firstLine.substring(1);
        }
        String[] parts = firstLine.split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }

    private String getCurrentDate() {
        return java.time.LocalDate.now().toString();
    }

}
