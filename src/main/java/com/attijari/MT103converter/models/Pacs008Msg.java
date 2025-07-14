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
        //Todo: return contenu XML généré
        return xmlContent;
    }

    public boolean validateWithXSD(String xsdPath) {
        // Todo
        return true;
    }

    public String getXmlContent() {
        return xmlContent;
    }

    public void setXmlContent(String xmlContent) {
        this.xmlContent = xmlContent;
    }
}
