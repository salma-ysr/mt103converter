package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.ErrorCall;
import org.springframework.stereotype.Service;

import java.util.List;

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
        // Todo
        return new ErrorCall();
    }

    /**
     * Valide un XML pacs.008 avec schéma XSD.
     *
     * @param xml contenu XML à valider
     * @param xsdPath path vers le fichier XSD
     * @return liste d’erreurs
     */
    public ErrorCall validatePacs008(String xml, String xsdPath) {
        // Todo
        return new ErrorCall();
    }
}
