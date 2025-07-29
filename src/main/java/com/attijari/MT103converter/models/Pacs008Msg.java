package com.attijari.MT103converter.models;

/**
 * Représenter le message ISO 20022 (pacs.008)
 */
public class Pacs008Msg {

    private String xmlContent;

    public Pacs008Msg() {}

    public Pacs008Msg(String xmlContent) {
        this.xmlContent = xmlContent;
    }

    public String generateXML() {
        // Génère un XML pacs.008 basique à partir du contenu
        if (xmlContent != null && !xmlContent.isEmpty()) {
            return xmlContent;
        }
        // Exemple de squelette minimal si xmlContent est vide
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">\n" +
               "  <FIToFICustomerCreditTransfer>\n" +
               "    <!-- Ajoutez ici les éléments requis -->\n" +
               "  </FIToFICustomerCreditTransfer>\n" +
               "</Document>";
    }

    public String getXmlContent() {
        return xmlContent;
    }

    public void setXmlContent(String xmlContent) {
        this.xmlContent = xmlContent;
    }
}
