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
            return new ConversionResult(false, null, "Le message MT103 est vide.", null);
        }

        //parser
        MT103Msg mt103 = parser.parse(rawMT103);

        //valider MT103
        ErrorCall mt103Errors = validator.validateMT103(mt103);
        if (mt103Errors.hasErrors()) {
            StringBuilder errorMsg = new StringBuilder();
            for (String err : mt103Errors.getErrors()) {
                errorMsg.append("• ").append(err).append("\n");
            }
            // Retourner l'objet MT103 même en cas d'erreur pour pouvoir le sauvegarder
            return new ConversionResult(false, null, errorMsg.toString().trim(), mt103);
        }

        // transformer
        Pacs008Msg pacs = transformer.transform(mt103);
        if (pacs == null || pacs.getXmlContent() == null) {
            return new ConversionResult(false, null, "Erreur lors de la transformation du message MT103.", mt103);
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
            return new ConversionResult(false, null, errorMsg.toString().trim(), mt103);
        }

        // Succès : on ne sauvegarde plus ici car c'est fait dans le MT103Controller
        mt103.setPacs008Xml(xml);

        //retourner XML final avec l'objet MT103
        return new ConversionResult(true, xml, null, mt103);
    }

    public List<MT103Msg> getAllMessages() {
        return repository.findAll();
    }

    // Classe interne pour le résultat de conversion
    public static class ConversionResult {
        private boolean success;
        private String xmlContent;
        private String errorMessage;
        private MT103Msg mt103Msg;  // Ajouter l'objet MT103Msg

        public ConversionResult(boolean success, String xmlContent, String errorMessage, MT103Msg mt103Msg) {
            this.success = success;
            this.xmlContent = xmlContent;
            this.errorMessage = errorMessage;
            this.mt103Msg = mt103Msg;
        }

        // Constructeur de compatibilité (pour les anciens appels)
        public ConversionResult(boolean success, String xmlContent, String errorMessage) {
            this(success, xmlContent, errorMessage, null);
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

        public MT103Msg getMt103Msg() {
            return mt103Msg;
        }
    }

}
