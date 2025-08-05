package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.ErrorCall;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static final Logger logger = LogManager.getLogger(Validator.class);

    /**
     * Valide un objet MT103Msg selon des règles métier.
     *
     * @param msg le message MT103 à valider
     * @return une liste d’erreurs potentielles
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
            // Vérifier l'équilibre des accolades
            int open = 0, close = 0;
            for (char c : raw.toCharArray()) {
                if (c == '{') open++;
                if (c == '}') close++;
            }
            if (open != close) {
                errors.addError("Erreur : Le nombre d'accolades ouvrantes et fermantes dans le message MT103 n'est pas équilibré");
            }
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
}
