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

        try {
            // Vérifier et extraire d'abord les blocs MT103
            Map<String, String> blocks = extractBlocks(rawMessage);

            // Valider la présence des blocs obligatoires (1, 2, 4, 5 - pas 3)
            validateMandatoryBlocks(blocks);

            // Parser le contenu du bloc 4 qui contient les champs de transaction
            String block4 = blocks.get("4");
            if (block4 != null && !block4.isEmpty()) {
                fields = parseBlock4Fields(block4);
            }

            logger.info("MT103 parsing complete. Total tags: {}", fields.size());
            return new MT103Msg(rawMessage, fields);

        } catch (Exception e) {
            logger.error("Erreur lors du parsing MT103: {}", e.getMessage());
            // En cas d'erreur, utiliser l'ancienne méthode comme fallback
            return parseWithFallbackMethod(rawMessage);
        }
    }

    /**
     * Extrait les différents blocs d'un message MT103
     */
    private Map<String, String> extractBlocks(String rawMessage) {
        Map<String, String> blocks = new HashMap<>();

        // Nettoyer le message et supprimer les espaces/retours à la ligne inutiles
        String cleanMessage = rawMessage.replaceAll("\\s+", " ").trim();

        // Pattern pour identifier les blocs {X:...}
        int pos = 0;
        while (pos < cleanMessage.length()) {
            if (cleanMessage.charAt(pos) == '{') {
                int colonPos = cleanMessage.indexOf(':', pos);
                if (colonPos > pos + 1) {
                    String blockNumber = cleanMessage.substring(pos + 1, colonPos);

                    // Trouver la fin du bloc en comptant les accolades
                    int braceCount = 1;
                    int endPos = colonPos + 1;
                    while (endPos < cleanMessage.length() && braceCount > 0) {
                        if (cleanMessage.charAt(endPos) == '{') {
                            braceCount++;
                        } else if (cleanMessage.charAt(endPos) == '}') {
                            braceCount--;
                        }
                        endPos++;
                    }

                    if (braceCount == 0) {
                        // Bloc trouvé - extraire le contenu (sans les accolades)
                        String blockContent = cleanMessage.substring(colonPos + 1, endPos - 1);
                        blocks.put(blockNumber, blockContent);
                        logger.debug("Bloc {} extrait: {}", blockNumber, blockContent.length() > 50 ? blockContent.substring(0, 50) + "..." : blockContent);
                    }

                    pos = endPos;
                } else {
                    pos++;
                }
            } else {
                pos++;
            }
        }

        return blocks;
    }

    /**
     * Valide la présence des blocs obligatoires (1, 2, 4, 5)
     */
    private void validateMandatoryBlocks(Map<String, String> blocks) {
        String[] mandatoryBlocks = {"1", "2", "4", "5"};

        for (String blockNumber : mandatoryBlocks) {
            if (!blocks.containsKey(blockNumber) || blocks.get(blockNumber).trim().isEmpty()) {
                throw new IllegalArgumentException("Bloc obligatoire {" + blockNumber + ":...} manquant dans le message MT103");
            }
        }

        logger.debug("Tous les blocs obligatoires (1, 2, 4, 5) sont présents");
    }

    /**
     * Parse les champs du bloc 4 (contenu de la transaction)
     */
    private Map<String, String> parseBlock4Fields(String block4Content) {
        Map<String, String> fields = new HashMap<>();

        // Le bloc 4 commence généralement par un space et se termine par " -"
        String content = block4Content.trim();
        if (content.startsWith(" ")) {
            content = content.substring(1);
        }
        if (content.endsWith(" -")) {
            content = content.substring(0, content.length() - 2);
        }

        // Split par les tags qui commencent par ":",
        // en s'assurant de ne pas couper les valeurs contenant des sauts de ligne
        String[] parts = content.split("(?=:[0-9]{2}[A-Z]?:)");

        for (String part : parts) {
            part = part.trim();
            if (part.startsWith(":")) {
                int secondColon = part.indexOf(":", 1);
                if (secondColon > 1) {
                    String tag = part.substring(1, secondColon);
                    String value = part.substring(secondColon + 1).trim();

                    // Nettoyer la valeur - supprimer les caractères de fin de bloc
                    value = value.replaceAll("\\s*-\\}.*$", "").trim();

                    fields.put(tag, value);
                    logger.trace("Champ extrait - Tag: {}, Valeur: {}", tag, value);
                }
            }
        }

        return fields;
    }

    /**
     * Méthode de fallback en cas d'erreur avec la nouvelle méthode
     */
    private MT103Msg parseWithFallbackMethod(String rawMessage) {
        logger.warn("Utilisation de la méthode de fallback pour le parsing");
        Map<String, String> fields = new HashMap<>();

        // Ancienne méthode ligne par ligne comme backup
        String[] lines = rawMessage.split("\\r?\\n");
        String currentTag = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith(":")) {
                // sauvegarder le champ précédent
                if (currentTag != null) {
                    String cleanValue = currentValue.toString().trim();
                    // Nettoyer la valeur pour enlever les caractères de fin de bloc
                    cleanValue = cleanValue.replaceAll("\\s*-\\}.*$", "").trim();
                    fields.put(currentTag, cleanValue);
                }

                // nouveau tag
                int secondColon = line.indexOf(":", 1);
                if (secondColon > 1) {
                    currentTag = line.substring(1, secondColon);
                    String value = line.substring(secondColon + 1).trim();
                    currentValue = new StringBuilder(value);
                } else {
                    logger.warn("Invalid tag format in line: {}", line);
                }
            } else if (currentTag != null && !line.trim().startsWith("-}") && !line.trim().startsWith("{5:")) {
                // ligne sans `:`, ajouter au champ précédent seulement si ce n'est pas la fin du bloc
                currentValue.append(" ").append(line.trim());
            }
        }

        // sauvegarder le dernier champ
        if (currentTag != null) {
            String cleanValue = currentValue.toString().trim();
            cleanValue = cleanValue.replaceAll("\\s*-\\}.*$", "").trim();
            fields.put(currentTag, cleanValue);
        }

        return new MT103Msg(rawMessage, fields);
    }
}
