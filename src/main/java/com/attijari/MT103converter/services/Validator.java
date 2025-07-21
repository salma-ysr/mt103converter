package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.ErrorCall;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXException;

/**
 * valider les messages MT103 et pacs.008
 */
@Service
public class Validator {

    /**
     * Valide un objet MT103Msg selon des règles métier.
     *
     * @param msg le message MT103 à valider
     * @return une liste d’erreurs potentielles
     */
    public ErrorCall validateMT103(MT103Msg msg){
        ErrorCall errors = new ErrorCall();
        // Liste des tags obligatoires pour MT103
        String[] requiredTags = {"20", "23B", "32A", "50A", "50K", "59", "71A"};
        for (String tag : requiredTags) {
            String value = msg.getField(tag);
            if (value == null || value.trim().isEmpty()) {
                errors.addError("Champ obligatoire manquant ou vide: " + tag);
            }
        }
        // Si ni 50A ni 50K n'est présent, erreur
        if (msg.getField("50A").isEmpty() && msg.getField("50K").isEmpty()) {
            errors.addError("Un des champs 50A ou 50K doit être présent.");
        }
        return errors;
    }


    public ErrorCall validatePacs008(String xml) {
        String xsdPath = "src/main/resources/schemas/pacs.008.001.08.xsd";
        ErrorCall errors = new ErrorCall();
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new File(xsdPath));
            javax.xml.validation.Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new java.io.StringReader(xml)));
        } catch (SAXException | IOException e) {
            errors.addError(e.getMessage());
        }
        return errors;
    }
}
