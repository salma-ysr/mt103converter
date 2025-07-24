package com.attijari.MT103converter.converters;

import com.attijari.MT103converter.models.ErrorCall;
import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.Pacs008Msg;
import com.attijari.MT103converter.repositories.MT103MsgRepository;
import com.attijari.MT103converter.services.MT103Parser;
import com.attijari.MT103converter.services.Transformer;
import com.attijari.MT103converter.services.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class MT103ToPacs008Converter {

    @Autowired
    private MT103Parser parser;

    @Autowired
    private Validator validator;

    @Autowired
    private Transformer transformer;

    @Autowired
    private MT103MsgRepository repository;

    public ConversionResult process(String rawMT103) {
        if (rawMT103 == null || rawMT103.isBlank()) {
            return new ConversionResult(false, null, "Le message MT103 est vide.");
        }

        //parser
        MT103Msg mt103 = parser.parse(rawMT103);

        // sauvegarder
        repository.save(mt103);
        System.out.println("Message sauvegardé sur MongoDB avec ID: " + mt103.getId());


        //valider MT103
        ErrorCall mt103Errors = validator.validateMT103(mt103);
        if (mt103Errors.hasErrors()) {
            StringBuilder errorMsg = new StringBuilder();
            for (String err : mt103Errors.getErrors()) {
                errorMsg.append("• ").append(err).append("\n");
            }
            return new ConversionResult(false, null, errorMsg.toString().trim());
        }

        // transformer
        Pacs008Msg pacs = transformer.transform(mt103);
        if (pacs == null || pacs.getXmlContent() == null) {
            return new ConversionResult(false, null, "Erreur lors de la transformation du message MT103.");
        }

        // générer XML
        String xml = pacs.generateXML();

        // valider XML avec XSD
        ErrorCall pacsErrors = validator.validatePacs008(xml);
        if (pacsErrors.hasErrors()) {
            StringBuilder errorMsg = new StringBuilder();
            for (String err : pacsErrors.getErrors()) {
                errorMsg.append("• ").append(err).append("\n");
            }
            return new ConversionResult(false, null, errorMsg.toString().trim());
        }

        //retourner XML final
        return new ConversionResult(true, xml, null);
    }

    public List<MT103Msg> getAllMessages() {
        return repository.findAll();
    }

    // Classe interne pour le résultat de conversion
    public static class ConversionResult {
        private boolean success;
        private String xmlContent;
        private String errorMessage;

        public ConversionResult(boolean success, String xmlContent, String errorMessage) {
            this.success = success;
            this.xmlContent = xmlContent;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getXmlContent() {
            return xmlContent;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
