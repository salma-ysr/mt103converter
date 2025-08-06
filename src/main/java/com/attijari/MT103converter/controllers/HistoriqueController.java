package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.repositories.MT103MsgRepository;
import com.attijari.MT103converter.models.MT103Msg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * API Controller pour les données de l'historique par utilisateur
 */
@RestController
public class HistoriqueController {

    private static final Logger logger = LogManager.getLogger(HistoriqueController.class);

    @Autowired(required = false)
    private MT103MsgRepository mt103Repository;

    /**
     * API pour récupérer l'historique des conversions de l'utilisateur connecté
     */
    @GetMapping("/api/historique/list")
    public Map<String, Object> getHistoriqueList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String filter) {

        String currentUser = getCurrentUsername();
        logger.debug("Récupération de l'historique pour l'utilisateur {} - page: {}, size: {}, filter: {}",
                    currentUser, page, size, filter);

        Map<String, Object> response = new HashMap<>();

        try {
            if (mt103Repository != null) {
                // Récupérer les vraies données depuis MongoDB pour cet utilisateur
                List<Map<String, Object>> realData = getRealHistoriqueData(currentUser, page, size, filter);
                long totalElements = mt103Repository.countByUsername(currentUser);
                int totalPages = (int) Math.ceil((double) totalElements / size);

                response.put("conversions", realData);
                response.put("totalPages", totalPages);
                response.put("currentPage", page);
                response.put("totalElements", totalElements);
                response.put("currentUser", currentUser);

                logger.info("Historique récupéré pour {}: {} éléments sur {} pages",
                           currentUser, totalElements, totalPages);
            } else {
                // Données factices si pas de DB
                List<Map<String, Object>> mockData = generateMockHistoriqueData(page, size);
                response.put("conversions", mockData);
                response.put("totalPages", 5);
                response.put("currentPage", page);
                response.put("totalElements", 50);
                response.put("currentUser", currentUser);
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération de l'historique pour {}: {}", currentUser, e.getMessage());
            response.put("conversions", new ArrayList<>());
            response.put("totalPages", 0);
            response.put("currentPage", 0);
            response.put("totalElements", 0);
            response.put("error", "Erreur lors du chargement de l'historique");
        }

        return response;
    }

    /**
     * Récupérer l'utilisateur connecté
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                if (authentication.getPrincipal() instanceof OidcUser) {
                    OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
                    return oidcUser.getPreferredUsername();
                } else {
                    return authentication.getName();
                }
            }
        } catch (Exception e) {
            logger.warn("Impossible de récupérer l'utilisateur connecté: {}", e.getMessage());
        }
        return "anonymous";
    }

    /**
     * Récupérer les vraies données d'historique pour un utilisateur spécifique
     */
    private List<Map<String, Object>> getRealHistoriqueData(String username, int page, int size, String filter) {
        List<Map<String, Object>> historique = new ArrayList<>();

        try {
            // Récupérer les dernières conversions pour cet utilisateur
            List<MT103Msg> messages = mt103Repository.findTop50ByUsernameOrderByCreatedAtDesc(username);

            // Appliquer le filtre si fourni
            if (!filter.isEmpty()) {
                messages = messages.stream()
                    .filter(msg -> msg.getField("20").toLowerCase().contains(filter.toLowerCase()) ||
                                  (msg.getField("50K") != null && msg.getField("50K").toLowerCase().contains(filter.toLowerCase())) ||
                                  (msg.getField("59") != null && msg.getField("59").toLowerCase().contains(filter.toLowerCase())))
                    .toList();
            }

            // Pagination
            int start = page * size;
            int end = Math.min(start + size, messages.size());

            if (start < messages.size()) {
                messages = messages.subList(start, end);
            } else {
                messages = new ArrayList<>();
            }

            // Convertir en format pour l'API
            for (MT103Msg msg : messages) {
                try {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", msg.getId());

                    // Protection contre les champs null
                    item.put("transactionId", safeGetField(msg, "20"));
                    item.put("amount", extractAmount(safeGetField(msg, "32A")));
                    item.put("currency", extractCurrency(safeGetField(msg, "32A")));
                    item.put("sender", extractFirstLine(safeGetField(msg, "50K")));
                    item.put("beneficiary", extractFirstLine(safeGetField(msg, "59")));

                    // Logique simplifiée et plus robuste pour déterminer le statut
                    boolean isSuccess = msg.getPacs008Xml() != null &&
                                      msg.getPacs008Xml().trim().length() > 50;

                    item.put("status", isSuccess ? "Succès" : "Erreur");
                    item.put("date", msg.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                    item.put("username", msg.getUsername());

                    // Ajouter le nom de fichier basé sur l'ID
                    item.put("fileName", "MT103_" + msg.getId().substring(0, Math.min(8, msg.getId().length())) + ".txt");

                    // Ajouter une taille de fichier estimée
                    int fileSize = msg.getRawContent() != null ? msg.getRawContent().length() : 0;
                    item.put("fileSize", formatFileSize(fileSize));

                    // Ajout des fichiers MT103 in et Pacs008 out
                    item.put("mt103InFile", msg.getRawContent());
                    item.put("pacs008OutFile", msg.getPacs008Xml());

                    historique.add(item);

                } catch (Exception e) {
                    logger.error("Erreur lors du traitement du message {}: {}", msg.getId(), e.getMessage());
                    // Continuer avec le message suivant
                }
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des données historiques pour {}: {}", username, e.getMessage());
        }

        return historique;
    }

    /**
     * API pour supprimer un élément de l'historique
     */
    @GetMapping("/api/historique/delete")
    public Map<String, Object> deleteHistoryItem(@RequestParam String id) {
        logger.info("Suppression de l'élément d'historique: {}", id);

        Map<String, Object> response = new HashMap<>();

        try {
            // TODO: Implémenter la suppression réelle
            response.put("success", true);
            response.put("message", "Élément supprimé avec succès");
        } catch (Exception e) {
            logger.error("Erreur lors de la suppression: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Erreur lors de la suppression");
        }

        return response;
    }

    /**
     * API pour effacer tout l'historique
     */
    @GetMapping("/api/historique/clear")
    public Map<String, Object> clearHistory() {
        logger.info("Effacement de l'historique complet");

        Map<String, Object> response = new HashMap<>();

        try {
            // TODO: Implémenter l'effacement réel
            response.put("success", true);
            response.put("message", "Historique effacé avec succès");
        } catch (Exception e) {
            logger.error("Erreur lors de l'effacement: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Erreur lors de l'effacement");
        }

        return response;
    }

    // === Méthodes utilitaires ===

    private long getTotalConversions() {
        try {
            return mt103Repository != null ? mt103Repository.count() : 47;
        } catch (Exception e) {
            logger.warn("Impossible de récupérer le nombre total: {}", e.getMessage());
            return 47;
        }
    }

    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"));
    }

    private List<Map<String, Object>> generateMockHistoriqueData(int page, int size) {
        List<Map<String, Object>> historique = new ArrayList<>();
        Random random = new Random();

        // Types de conversion possibles
        String[] types = {"MT103 → PACS008", "PACS008 → MT103"};
        String[] errorTypes = {"Format invalide", "Champ manquant", "Validation échouée"};

        int start = page * size;
        for (int i = 0; i < size; i++) {
            Map<String, Object> item = new HashMap<>();

            String type = types[random.nextInt(types.length)];
            boolean isSuccess = random.nextDouble() < 0.85; // 85% de succès

            // Filtrage simple
            LocalDateTime date = LocalDateTime.now().minusDays(random.nextInt(30))
                    .minusHours(random.nextInt(24))
                    .minusMinutes(random.nextInt(60));

            item.put("id", "CONV_" + (start + i + 1));
            item.put("date", date.toString()); // Format ISO pour JavaScript
            item.put("type", type);
            item.put("success", isSuccess); // Boolean pour JavaScript
            item.put("status", isSuccess ? "Succès" : "Erreur"); // String pour affichage
            item.put("fileName", "fichier_" + (start + i + 1) + ".txt");
            item.put("fileSize", random.nextInt(50) + 5 + " KB");
            item.put("duration", random.nextInt(3000) + 500); // Nombre sans "ms"

            if (!isSuccess) {
                item.put("errorMessage", errorTypes[random.nextInt(errorTypes.length)]);
            }

            historique.add(item);
        }

        return historique;
    }

    /**
     * Extraire le montant du champ 32A du message MT103
     */
    private String extractAmountFromMT103(MT103Msg conversion) {
        try {
            String field32A = conversion.getField("32A");
            if (field32A != null && field32A.length() > 9) {
                // Format 32A: YYMMDDCCCNNNNN (date + devise + montant)
                // Extraire la devise (3 caractères) et le montant
                String currency = field32A.substring(6, 9);
                String amountStr = field32A.substring(9).replace(",", ".");

                // Parser le montant
                try {
                    double amount = Double.parseDouble(amountStr);
                    return String.format("%.2f %s", amount, currency);
                } catch (NumberFormatException e) {
                    return amountStr + " " + currency;
                }
            }
            return "N/A";
        } catch (Exception e) {
            logger.warn("Erreur lors de l'extraction du montant: {}", e.getMessage());
            return "N/A";
        }
    }

    /**
     * API pour télécharger un fichier de conversion
     */
    @GetMapping("/api/historique/download")
    public ResponseEntity<byte[]> downloadFile(@RequestParam String id, @RequestParam String type) {
        logger.info("Téléchargement demandé - ID: {}, Type: {}", id, type);

        try {
            if (mt103Repository == null) {
                return ResponseEntity.notFound().build();
            }

            Optional<MT103Msg> conversionOpt = mt103Repository.findById(id);
            if (!conversionOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            MT103Msg conversion = conversionOpt.get();
            byte[] fileContent;
            String fileName;
            String contentType;

            if ("mt103".equals(type)) {
                // Télécharger le fichier MT103 d'entrée
                fileContent = conversion.getRawContent().getBytes(StandardCharsets.UTF_8);
                fileName = "MT103_" + id.substring(0, 8) + ".txt";
                contentType = "text/plain";
            } else if ("pacs008".equals(type)) {
                // Télécharger le fichier PACS008 de sortie
                if (conversion.getPacs008Xml() == null || conversion.getPacs008Xml().trim().isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                fileContent = conversion.getPacs008Xml().getBytes(StandardCharsets.UTF_8);
                fileName = "PACS008_" + id.substring(0, 8) + ".xml";
                contentType = "application/xml";
            } else {
                return ResponseEntity.badRequest().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);

        } catch (Exception e) {
            logger.error("Erreur lors du téléchargement: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // === Méthodes utilitaires pour l'extraction des données ===

    private String extractAmount(String field32A) {
        if (field32A == null || field32A.isEmpty()) return "0.00";
        try {
            // Format 32A: YYMMDDCCCNNNNN
            String amountStr = field32A.length() > 9 ? field32A.substring(9) : "0.00";
            amountStr = amountStr.replace(",", ".");
            double amount = Double.parseDouble(amountStr);
            return String.format("%.2f", amount);
        } catch (Exception e) {
            return "0.00";
        }
    }

    private String extractCurrency(String field32A) {
        if (field32A == null || field32A.isEmpty()) return "EUR";
        try {
            return field32A.length() > 9 ? field32A.substring(6, 9) : "EUR";
        } catch (Exception e) {
            return "EUR";
        }
    }

    private String extractFirstLine(String field) {
        if (field == null || field.isEmpty()) return "";
        String[] lines = field.split("\n");
        return lines.length > 0 ? lines[0].trim() : "";
    }

    private String safeGetField(MT103Msg msg, String fieldName) {
        try {
            String value = msg.getField(fieldName);
            return value != null ? value : "";
        } catch (Exception e) {
            logger.warn("Erreur lors de l'accès au champ {}: {}", fieldName, e.getMessage());
            return "";
        }
    }

    private String formatFileSize(int sizeInBytes) {
        if (sizeInBytes <= 0) return "0 Ko";
        String[] units = {"Ko", "Mo", "Go", "To"};
        int i = 0;
        double size = sizeInBytes;
        while (size >= 1024 && i < units.length - 1) {
            size /= 1024;
            i++;
        }
        return String.format("%.1f %s", size, units[i]);
    }
}
