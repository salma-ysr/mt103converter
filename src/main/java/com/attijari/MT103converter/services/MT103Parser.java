package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 MT103Parser.java : Parser un message MT103 en format texte et le convertir en objet MT103Msg
 **/
@Service
public class MT103Parser {
    private static final Logger logger = LogManager.getLogger(MT103Parser.class);

    /**
     Prend un message texte MT103 et extrait les champs en clé-valeur
     @param rawMessage message MT103 en String
     @return objet MT103Msg avec contenu brut et une map des champs
     **/

    public MT103Msg parse(String rawMessage) {
        logger.debug("Parsing MT103 message");
        Map<String, String> fields = new HashMap<>();

        // split par lignes
        String[] lines = rawMessage.split("\\r?\\n");

        String currentTag = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith(":")) {
                // sauvegarder le champ précédent
                if (currentTag != null) {
                    fields.put(currentTag, currentValue.toString().trim());
                    logger.trace("Parsed tag: {} value: {}", currentTag, currentValue.toString().trim());
                }

                // nouveau tag
                int secondColon = line.indexOf(":", 1);
                if (secondColon > 1) {
                    currentTag = line.substring(1, secondColon);
                    currentValue = new StringBuilder(line.substring(secondColon + 1).trim());
                } else {
                    logger.warn("Invalid tag format in line: {}", line);
                }
            } else if (currentTag != null) {
                // ligne sans `:`, ajouter au champ précédent
                currentValue.append("\n").append(line.trim());
            }
        }

        //sauvegarder le dernier champ
        if (currentTag != null) {
            fields.put(currentTag, currentValue.toString().trim());
            logger.trace("Parsed tag: {} value: {}", currentTag, currentValue.toString().trim());
        }
        logger.info("MT103 parsing complete. Total tags: {}", fields.size());
        return new MT103Msg(rawMessage, fields);
    }

}
