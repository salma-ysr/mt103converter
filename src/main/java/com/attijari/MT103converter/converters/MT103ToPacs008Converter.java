package com.attijari.MT103converter.converters;

import com.attijari.MT103converter.models.ErrorCall;
import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.Pacs008Msg;
import com.attijari.MT103converter.services.MT103Parser;
import com.attijari.MT103converter.services.Transformer;
import com.attijari.MT103converter.services.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MT103ToPacs008Converter {

    @Autowired
    private MT103Parser parser;

    @Autowired
    private Validator validator;

    @Autowired
    private Transformer transformer;

    private static final String XSD_PATH = "schemas/pacs.008.001.02.xsd";

    public String process(String rawMT103) {
        if (rawMT103 == null || rawMT103.isBlank()) {
            return "<error>Erreur: Le message est vide.</error>";
        }

        //parser
        MT103Msg mt103 = parser.parse(rawMT103);

        //valider MT103
        ErrorCall mt103Errors = validator.validateMT103(mt103);
        if (mt103Errors.hasErrors()) {
            return buildErrorXml("Erreur: Validation du MT103 échouée.", mt103Errors);
        }

        // transformer
        Pacs008Msg pacs = transformer.transform(mt103);
        if (pacs == null || pacs.getXmlContent() == null) {
            return "<error>Erreur: Transformation échouée.</error>";
        }

        // générer XML
        String xml = pacs.generateXML();

        // valider XML avec XSD
        ErrorCall pacsErrors = validator.validatePacs008(xml, XSD_PATH);
        if (pacsErrors.hasErrors()) {
            return buildErrorXml("Erreur: Validation XML du pacs.008 échouée.", pacsErrors);
        }

        //retourner XML final
        return xml;
    }

    private String buildErrorXml(String title, ErrorCall errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("<error>\n");
        sb.append("  <title>").append(title).append("</title>\n");
        for (String err : errors.getErrors()) {
            sb.append("  <detail>").append(err).append("</detail>\n");
        }
        sb.append("</error>");
        return sb.toString();
    }
}
