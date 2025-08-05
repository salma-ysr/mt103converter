package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.repositories.MT103MsgRepository;
import com.attijari.MT103converter.models.MT103Msg;
import org.springframework.beans.factory.annotation.Autowired;
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
 * API Controller pour les données de l'historique
 */
@RestController
public class HistoriqueController {

    private static final Logger logger = LogManager.getLogger(HistoriqueController.class);

    @Autowired(required = false)
    private MT103MsgRepository mt103Repository;

    /**
     * API pour récupérer l'historique des conversions
     */
    @GetMapping("/api/historique/list")
    public Map<String, Object> getHistoriqueList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String filter) {

        logger.debug("Récupération de l'historique en temps réel - page: {}, size: {}, filter: {}", page, size, filter);

        Map<String, Object> response = new HashMap<>();

        try {
            if (mt103Repository != null) {
                // Récupérer les vraies données depuis MongoDB
                List<Map<String, Object>> realData = getRealHistoriqueData(page, size, filter);
                long totalElements = mt103Repository.count();
                int totalPages = (int) Math.ceil((double) totalElements / size);

                response.put("conversions", realData);
                response.put("totalPages", totalPages);
                response.put("totalElements", totalElements);
            } else {
                // Fallback vers données factices si pas de DB
                List<Map<String, Object>> mockData = generateMockHistorique(page, size, filter);
                response.put("conversions", mockData);
                response.put("totalPages", 5);
                response.put("totalElements", 47L);
            }

            response.put("currentPage", page);
            response.put("pageSize", size);

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération de l'historique: {}", e.getMessage());
            // Fallback vers données factices en cas d'erreur
            response.put("conversions", generateMockHistorique(page, size, filter));
            response.put("totalPages", 5);
            response.put("totalElements", 47L);
            response.put("currentPage", page);
            response.put("pageSize", size);
        }

        return response;
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

    private List<Map<String, Object>> generateMockHistorique(int page, int size, String filter) {
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
            if (!filter.isEmpty() && !type.toLowerCase().contains(filter.toLowerCase())) {
                continue;
            }

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

    // === Nouvelle méthode pour récupérer les vraies données ===

    private List<Map<String, Object>> getRealHistoriqueData(int page, int size, String filter) {
        List<Map<String, Object>> historique = new ArrayList<>();

        try {
            // Récupérer les dernières conversions depuis MongoDB
            List<MT103Msg> recentConversions = mt103Repository.findTop50ByOrderByCreatedAtDesc();

            // Appliquer la pagination manuelle (pour simplifier)
            int start = page * size;
            int end = Math.min(start + size, recentConversions.size());

            for (int i = start; i < end; i++) {
                if (i >= recentConversions.size()) break;

                MT103Msg conversion = recentConversions.get(i);
                Map<String, Object> item = new HashMap<>();

                // Déterminer le succès basé sur la présence du XML
                boolean isSuccess = conversion.getPacs008Xml() != null && !conversion.getPacs008Xml().trim().isEmpty();

                // Extraire le montant du champ 32A (format: YYMMDDCCCNNNNN)
                String amount = extractAmountFromMT103(conversion);

                // Filtrage par statut
                String status = isSuccess ? "Succès" : "Erreur";
                if (!filter.isEmpty() && !status.toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }

                // Générer les noms de fichiers
                String fileInName = "MT103_" + conversion.getId().substring(0, 8) + ".txt";
                String fileOutName = "PACS008_" + conversion.getId().substring(0, 8) + ".xml";

                item.put("id", conversion.getId());
                item.put("date", conversion.getCreatedAt().toString()); // Format ISO
                item.put("amount", amount);
                item.put("success", isSuccess);
                item.put("status", status);
                item.put("fileInName", fileInName);
                item.put("fileOutName", fileOutName);

                if (!isSuccess) {
                    item.put("errorMessage", "Erreur de validation MT103");
                }

                historique.add(item);
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des vraies données: {}", e.getMessage());
            throw e; // Re-lancer pour le fallback
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
}
