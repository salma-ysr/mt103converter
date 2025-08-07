package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.ErrorCall;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private static final Logger logger = LogManager.getLogger(Validator.class);

    /**
     * Valide un objet MT103Msg selon des règles métier.
     *
     * @param msg le message MT103 à valider
     * @return une liste d'erreurs potentielles
     */
    public ErrorCall validateMT103(MT103Msg msg){
        logger.debug("Validating MT103Msg");
        ErrorCall errors = new ErrorCall();

        // Vérification de la structure globale du message MT103
        String raw = msg.getRawContent();
        if (raw == null || raw.isEmpty()) {
            errors.addError("Erreur : Le contenu brut du message MT103 est vide ou manquant");
        } else {
            if (!raw.trim().startsWith("{") || !raw.trim().endsWith("}")) {
                errors.addError("Erreur : Le message MT103 doit commencer par '{' et se terminer par '}'");
            }

            // Nouvelle validation stricte de la structure des blocs MT103
            ErrorCall structureErrors = validateMT103BlockStructure(raw.trim());
            errors.addAllErrors(structureErrors.getErrors());
        }

        // Vérification du champ 20 (Reference Transaction)
        String value = msg.getField("20");
        if (value == null || value.trim().isEmpty()) {
            errors.addError("Erreur : Le champ Référence de transaction (:20) est manquant");
            logger.warn("Missing tag :20 (Référence de transaction)");
        }

        // Vérification du champ 23B (Bank Operation Code)
        value = msg.getField("23B");
        if (value == null || value.trim().isEmpty()) {
            errors.addError("Erreur : Le champ Code opération bancaire (:23B) est manquant");
            logger.warn("Missing tag :23B (Code opération bancaire)");
        }

        // Vérification du champ 32A (Value Date/Currency/Amount)
        value = msg.getField("32A");
        if (value == null || value.trim().isEmpty()) {
            errors.addError("Erreur : Le champ Date valeur/Devise/Montant (:32A) est manquant");
            logger.warn("Missing tag :32A (Date valeur/Devise/Montant)");
        }

        // Vérification du champ 59 (Beneficiary)
        value = msg.getField("59");
        if (value == null || value.trim().isEmpty()) {
            errors.addError("Erreur : Le champ Bénéficiaire (:59) est manquant");
            logger.warn("Missing tag :59 (Bénéficiaire)");
        }

        // Vérification du champ 71A (Charges)
        value = msg.getField("71A");
        if (value == null || value.trim().isEmpty()) {
            errors.addError("Erreur : Le champ Répartition des frais (:71A) est manquant");
            logger.warn("Missing tag :71A (Répartition des frais)");
        }

        // Vérification du champ 33B (Currency/Amount) - OBLIGATOIRE
        value = msg.getField("33B");
        if (value == null || value.trim().isEmpty()) {
            errors.addError("Erreur : Le champ Devise/Montant (:33B) est manquant");
            logger.warn("Missing tag :33B (Devise/Montant)");
        }

        // Vérification du champ 70 (Remittance Information) - OBLIGATOIRE
        value = msg.getField("70");
        if (value == null || value.trim().isEmpty()) {
            errors.addError("Erreur : Le champ Texte descriptif (:70) est manquant");
            logger.warn("Missing tag :70 (Texte descriptif)");
        }

        // Si ni 50A ni 50K n'est présent, erreur
        if ((msg.getField("50A") == null || msg.getField("50A").trim().isEmpty()) &&
            (msg.getField("50K") == null || msg.getField("50K").trim().isEmpty())) {
            errors.addError("Erreur : Le champ Donneur d'ordre (:50A ou :50K) est manquant");
        }

        // Validation additionnelle du format des champs si présents
        validateFieldFormats(msg, errors);

        logger.info("Validation complete. Errors found: {}", errors.getErrors().size());
        return errors;
    }

    /**
     * Valide le format des champs présents pour donner des conseils d'amélioration
     */
    private void validateFieldFormats(MT103Msg msg, ErrorCall errors) {
        // Validation du champ 32A (format attendu: YYMMDDCCCNNNNN)
        String field32A = msg.getField("32A");
        if (field32A != null && !field32A.trim().isEmpty()) {
            if (field32A.length() < 9) {
                errors.addError("Erreur : Le champ :32A a un format incorrect. Format attendu: AAMMJJDDDMONTANT");
            }
        }

        // Validation du champ 71A (valeurs autorisées) - CORRECTION ICI
        String field71A = msg.getField("71A");
        if (field71A != null && !field71A.trim().isEmpty()) {
            String charges = field71A.trim().toUpperCase();
            // Enlever les espaces et caractères spéciaux pour une validation plus flexible
            charges = charges.replaceAll("\\s+", "").replaceAll("[^A-Z]", "");

            if (!charges.equals("OUR") && !charges.equals("BEN") && !charges.equals("SHA")) {
                errors.addError("Erreur : Le champ Répartition des frais (:71A) a une valeur incorrecte. " +
                    "Valeurs autorisées: OUR (payé par l'expéditeur), BEN (payé par le bénéficiaire), SHA (partagé). " +
                    "Valeur actuelle: " + field71A.trim());
            }
        }

        // Validation du champ 23B
        String field23B = msg.getField("23B");
        if (field23B != null && !field23B.trim().isEmpty()) {
            String code = field23B.trim().toUpperCase();
            if (!code.equals("CRED") && !code.equals("SPAY") && !code.equals("PHON") && !code.equals("HOLD")) {
                // Avertissement seulement, pas d'erreur bloquante
                System.out.println("Avertissement : Le champ :23B a une valeur inhabituelle (" + code + "). Codes recommandés: CRED, SPAY, PHON, HOLD");
            }
        }
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
            String friendlyMessage = convertTechnicalErrorToFriendlyMessage(e.getMessage());
            errors.addError(friendlyMessage);
        }
        return errors;
    }

    /**
     * Convertit les messages d'erreur techniques XSD en messages clairs pour l'utilisateur
     */
    private String convertTechnicalErrorToFriendlyMessage(String technicalError) {
        if (technicalError == null) {
            return "Erreur de validation XML inconnue";
        }

        // Erreur de longueur maximale
        if (technicalError.contains("cvc-maxLength-valid")) {
            return parseMaxLengthError(technicalError);
        }

        // Erreur de longueur minimale
        if (technicalError.contains("cvc-minLength-valid")) {
            return parseMinLengthError(technicalError);
        }

        // Erreur de pattern/format
        if (technicalError.contains("cvc-pattern-valid")) {
            return parsePatternError(technicalError);
        }

        // Erreur d'énumération (valeur non autorisée)
        if (technicalError.contains("cvc-enumeration-valid")) {
            return parseEnumerationError(technicalError);
        }

        // Erreur d'élément requis manquant
        if (technicalError.contains("cvc-complex-type") && technicalError.contains("expected")) {
            return parseMissingElementError(technicalError);
        }

        // Erreur de type de données
        if (technicalError.contains("cvc-datatype-valid")) {
            return parseDatatypeError(technicalError);
        }

        // Si aucun pattern reconnu, retourner un message générique plus clair
        return "Erreur de validation XML: " + simplifyTechnicalMessage(technicalError);
    }

    private String parseMaxLengthError(String error) {
        try {
            // Extraire la valeur et la longueur max
            String value = extractValueFromError(error);
            String maxLength = extractMaxLengthFromError(error);
            String fieldName = extractFieldNameFromError(error);

            if (value != null && maxLength != null) {
                return String.format("Erreur : Le champ %s dépasse la longueur maximale autorisée.\n" +
                    "• Valeur actuelle : \"%s\" (%d caractères)\n" +
                    "• Longueur maximale autorisée : %s caractères\n" +
                    "• Solution : Raccourcissez le texte de %d caractères",
                    fieldName != null ? fieldName : "adresse",
                    truncateValue(value), value.length(), maxLength,
                    value.length() - Integer.parseInt(maxLength));
            }
        } catch (Exception e) {
            // Si l'extraction échoue, message générique
        }
        return "Erreur : Un champ dépasse la longueur maximale autorisée. Veuillez raccourcir le texte.";
    }

    private String parseMinLengthError(String error) {
        try {
            String value = extractValueFromError(error);
            String minLength = extractMinLengthFromError(error);
            String fieldName = extractFieldNameFromError(error);

            if (value != null && minLength != null) {
                return String.format("Erreur : Le champ %s est trop court.\n" +
                    "• Valeur actuelle : \"%s\" (%d caractères)\n" +
                    "• Longueur minimale requise : %s caractères\n" +
                    "• Solution : Ajoutez %d caractères minimum",
                    fieldName != null ? fieldName : "texte",
                    value, value.length(), minLength,
                    Integer.parseInt(minLength) - value.length());
            }
        } catch (Exception e) {
            // Si l'extraction échoue
        }
        return "Erreur : Un champ ne respecte pas la longueur minimale requise.";
    }

    private String parsePatternError(String error) {
        String fieldName = extractFieldNameFromError(error);
        String value = extractValueFromError(error);

        // Identifier le type de format attendu
        if (error.contains("IBAN") || fieldName != null && fieldName.toLowerCase().contains("iban")) {
            return String.format("Format IBAN invalide: \"%s\". Format attendu: FR76 1234 5678 9012 3456 7890 123",
                truncateValue(value));
        }

        if (error.contains("BIC") || fieldName != null && fieldName.toLowerCase().contains("bic")) {
            return String.format("Format BIC invalide: \"%s\". Format attendu: 8 ou 11 caractères (ex: ABCDFRPP ou ABCDFRPPXXX)",
                truncateValue(value));
        }

        if (fieldName != null && fieldName.toLowerCase().contains("amount")) {
            return String.format("Format montant invalide: \"%s\". Format attendu: nombre décimal (ex: 1234.56)",
                truncateValue(value));
        }

        return String.format("Le champ %s a un format invalide: \"%s\".",
            fieldName != null ? fieldName : "XML", truncateValue(value));
    }

    private String parseEnumerationError(String error) {
        String value = extractValueFromError(error);
        String fieldName = extractFieldNameFromError(error);

        return String.format("Valeur non autorisée pour le champ %s: \"%s\". " +
            "Veuillez consulter la liste des valeurs autorisées.",
            fieldName != null ? fieldName : "XML", truncateValue(value));
    }

    private String parseMissingElementError(String error) {
        return "Un élément obligatoire est manquant dans le XML généré. " +
            "Cela peut indiquer un problème dans les données MT103 source.";
    }

    private String parseDatatypeError(String error) {
        String value = extractValueFromError(error);
        String fieldName = extractFieldNameFromError(error);

        return String.format("Type de données invalide pour le champ %s: \"%s\".",
            fieldName != null ? fieldName : "XML", truncateValue(value));
    }

    // Méthodes utilitaires pour extraire les informations des messages d'erreur
    private String extractValueFromError(String error) {
        try {
            int start = error.indexOf("Value '") + 7;
            int end = error.indexOf("' with", start);
            if (start > 6 && end > start) {
                return error.substring(start, end);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String extractMaxLengthFromError(String error) {
        try {
            int start = error.indexOf("maxLength '") + 11;
            int end = error.indexOf("'", start);
            if (start > 10 && end > start) {
                return error.substring(start, end);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String extractMinLengthFromError(String error) {
        try {
            int start = error.indexOf("minLength '") + 11;
            int end = error.indexOf("'", start);
            if (start > 10 && end > start) {
                return error.substring(start, end);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String extractFieldNameFromError(String error) {
        try {
            // Tenter d'extraire le nom du type ou de l'élément
            if (error.contains("type '")) {
                int start = error.indexOf("type '") + 6;
                int end = error.indexOf("'", start);
                if (start > 5 && end > start) {
                    String typeName = error.substring(start, end);
                    return simplifyFieldName(typeName);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String simplifyFieldName(String technicalName) {
        if (technicalName == null) return null;

        // Convertir les noms techniques en noms compréhensibles
        if (technicalName.contains("RestrictedFINXMax35Text")) return "Adresse/Nom";
        if (technicalName.contains("BICFIDec2014Identifier")) return "Code BIC";
        if (technicalName.contains("IBAN2007Identifier")) return "IBAN";
        if (technicalName.contains("ActiveCurrencyAndAmountSimpleType")) return "Montant";
        if (technicalName.contains("Max140Text")) return "Texte descriptif";
        if (technicalName.contains("ExternalPersonIdentification1Code")) return "Type d'identification";

        return technicalName;
    }

    private String truncateValue(String value) {
        if (value == null) return "null";
        if (value.length() <= 50) return value;
        return value.substring(0, 47) + "...";
    }

    private String simplifyTechnicalMessage(String message) {
        return message
            .replaceAll("cvc-[a-zA-Z-]+:", "")
            .replaceAll("facet-valid", "format valide")
            .replaceAll("with respect to", "par rapport à")
            .trim();
    }

    /**
     * Valide la structure des blocs dans un message MT103
     * Vérifie que chaque bloc est correctement encadré par des accolades
     * et que la structure générale est respectée
     */
    private ErrorCall validateMT103BlockStructure(String raw) {
        ErrorCall errors = new ErrorCall();

        // Supprimer les espaces et retours à la ligne pour une analyse plus précise
        String cleanRaw = raw.replaceAll("\\s+", "");

        // Vérifier que le message commence et se termine par des accolades
        if (!cleanRaw.startsWith("{") || !cleanRaw.endsWith("}")) {
            errors.addError("Erreur de structure : Le message MT103 doit commencer par '{' et se terminer par '}'");
            return errors;
        }

        // Vérifier l'équilibre des accolades
        int openCount = 0;
        int closeCount = 0;
        for (char c : cleanRaw.toCharArray()) {
            if (c == '{') openCount++;
            if (c == '}') closeCount++;
        }
        if (openCount != closeCount) {
            errors.addError("Erreur de structure : Nombre d'accolades ouvrantes (" + openCount +
                          ") et fermantes (" + closeCount + ") non équilibré");
        }

        // Extraire et valider les blocs principaux
        List<String> blocks = extractMT103Blocks(cleanRaw);

        if (blocks.isEmpty()) {
            errors.addError("Erreur de structure : Aucun bloc MT103 valide détecté");
            return errors;
        }

        // Vérifier la présence des blocs obligatoires (ignorer le bloc 5)
        boolean hasBlock1 = false, hasBlock2 = false, hasBlock4 = false;

        for (String block : blocks) {
            if (block.startsWith("{1:")) hasBlock1 = true;
            else if (block.startsWith("{2:")) hasBlock2 = true;
            else if (block.startsWith("{4:")) hasBlock4 = true;
            // Bloc 5 ignoré - pas obligatoire
        }

        if (!hasBlock1) errors.addError("Erreur de structure : Bloc {1: (Basic Header) manquant");
        if (!hasBlock2) errors.addError("Erreur de structure : Bloc {2: (Application Header) manquant");
        if (!hasBlock4) errors.addError("Erreur de structure : Bloc {4: (Text Block) manquant");
        // Bloc {5: (Trailer) ignoré dans la validation

        // Vérifier la structure interne du bloc 4 (le plus important)
        if (hasBlock4) {
            String block4 = getBlockContent(cleanRaw, "{4:");
            if (block4 != null && !block4.isEmpty()) {
                validateBlock4Structure(block4, errors);
            }
        }

        return errors;
    }

    /**
     * Extrait tous les blocs MT103 du message brut
     */
    private List<String> extractMT103Blocks(String cleanRaw) {
        List<String> blocks = new ArrayList<>();
        int index = 0;

        while (index < cleanRaw.length()) {
            int openBrace = cleanRaw.indexOf('{', index);
            if (openBrace == -1) break;

            // Trouver l'accolade fermante correspondante
            int braceCount = 1;
            int closeBrace = openBrace + 1;

            while (closeBrace < cleanRaw.length() && braceCount > 0) {
                char c = cleanRaw.charAt(closeBrace);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                closeBrace++;
            }

            if (braceCount == 0) {
                String block = cleanRaw.substring(openBrace, closeBrace);
                blocks.add(block);
                index = closeBrace;
            } else {
                // Accolade fermante manquante
                break;
            }
        }

        return blocks;
    }

    /**
     * Récupère le contenu d'un bloc spécifique
     */
    private String getBlockContent(String raw, String blockStart) {
        int start = raw.indexOf(blockStart);
        if (start == -1) return null;

        int openBrace = start;
        int braceCount = 1;
        int index = start + 1;

        while (index < raw.length() && braceCount > 0) {
            char c = raw.charAt(index);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            index++;
        }

        if (braceCount == 0) {
            return raw.substring(start, index);
        }

        return null;
    }

    /**
     * Valide la structure interne du bloc 4 (Text Block)
     */
    private void validateBlock4Structure(String block4, ErrorCall errors) {
        // Vérifier la présence des champs obligatoires dans le bloc 4
        String[] requiredFields = {":20:", ":23B:", ":32A:", ":33B:", ":59:", ":70:", ":71A:"};

        for (String field : requiredFields) {
            if (!block4.contains(field)) {
                String fieldName = getFieldName(field);
                errors.addError("Erreur dans le bloc {4: : Champ obligatoire " + field +
                              " (" + fieldName + ") manquant");
            }
        }

        // Vérifier qu'au moins un des champs :50A: ou :50K: est présent
        if (!block4.contains(":50A:") && !block4.contains(":50K:")) {
            errors.addError("Erreur dans le bloc {4: : Au moins un des champs :50A: ou :50K: (Donneur d'ordre) doit être présent");
        }
    }

    /**
     * Retourne le nom descriptif d'un champ MT103
     */
    private String getFieldName(String fieldTag) {
        switch (fieldTag) {
            case ":20:": return "Référence de transaction";
            case ":23B:": return "Code opération bancaire";
            case ":32A:": return "Date valeur/Devise/Montant";
            case ":33B:": return "Devise/Montant de change";
            case ":50A:": return "Donneur d'ordre (Option A)";
            case ":50K:": return "Donneur d'ordre (Option K)";
            case ":59:": return "Bénéficiaire";
            case ":70:": return "Détails du paiement";
            case ":71A:": return "Répartition des frais";
            default: return "Champ inconnu";
        }
    }
}
