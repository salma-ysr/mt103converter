package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 MT103Parser.java : Parser un message MT103 en format texte et le convertir en objet MT103Msg
 **/
@Service
public class MT103Parser {

    /**
     Prend un message texte MT103 et extrait les champs en clé-valeur
     @param rawMessage message MT103 en String
     @return objet MT103Msg avec contenu brut et une map des champs
     **/

    public MT103Msg parse(String rawMessage) {
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
                }

                // nouveau tag
                int secondColon = line.indexOf(":", 1);
                if (secondColon > 1) {
                    currentTag = line.substring(1, secondColon);
                    currentValue = new StringBuilder(line.substring(secondColon + 1).trim());
                }
            } else if (currentTag != null) {
                // ligne sans `:`, ajouter au champ précédent
                currentValue.append("\n").append(line.trim());
            }
        }

        //sauvegarder le dernier champ
        if (currentTag != null) {
            fields.put(currentTag, currentValue.toString().trim());
        }

        return new MT103Msg(rawMessage, fields);
    }

}
