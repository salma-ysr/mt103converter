package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.repositories.MT103MsgRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * API Controller pour les données du dashboard
 */
@RestController
public class DashboardController {
    private static final Logger logger = LogManager.getLogger(DashboardController.class);

    @Autowired(required = false)
    private MT103MsgRepository mt103Repository;

    /**
     * API pour récupérer les statistiques du dashboard par utilisateur
     */
    @GetMapping("/api/dashboard/stats")
    public Map<String, Object> getDashboardStats() {
        String currentUser = getCurrentUsername();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConversions", getTotalConversions(currentUser));
        stats.put("successRate", getSuccessRate(currentUser));
        stats.put("todayConversions", getTodayConversions(currentUser));
        stats.put("lastUpdate", getCurrentDateTime());
        stats.put("currentUser", currentUser);
        return stats;
    }

    /**
     * API pour les données des graphiques par utilisateur
     */
    @GetMapping("/api/dashboard/chart-data")
    public List<Map<String, Object>> getChartData() {
        String currentUser = getCurrentUsername();
        logger.debug("Récupération des données de graphiques pour l'utilisateur: {}", currentUser);
        return getRealTimelineData(currentUser);
    }

    /**
     * API pour les données du graphique en camembert (succès/erreurs)
     */
    @GetMapping("/api/dashboard/pie-data")
    public Map<String, Object> getPieData() {
        String currentUser = getCurrentUsername();
        logger.debug("Récupération des données de graphique en camembert pour l'utilisateur: {}", currentUser);

        Map<String, Object> pieData = new HashMap<>();

        try {
            if (mt103Repository != null) {
                // Récupérer tous les messages de l'utilisateur
                List<com.attijari.MT103converter.models.MT103Msg> messages =
                    mt103Repository.findTop50ByUsernameOrderByCreatedAtDesc(currentUser);

                // Compter les succès et erreurs
                long successCount = messages.stream()
                    .filter(msg -> msg.getPacs008Xml() != null && msg.getPacs008Xml().trim().length() > 50)
                    .count();

                long errorCount = messages.size() - successCount;

                pieData.put("success", successCount);
                pieData.put("errors", errorCount);
                pieData.put("total", messages.size());

                logger.debug("Données pie chart pour {}: {} succès, {} erreurs", currentUser, successCount, errorCount);
            } else {
                // Données par défaut si pas de repository
                pieData.put("success", 0);
                pieData.put("errors", 0);
                pieData.put("total", 0);
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des données pie chart pour {}: {}", currentUser, e.getMessage());
            // Données par défaut en cas d'erreur
            pieData.put("success", 0);
            pieData.put("errors", 0);
            pieData.put("total", 0);
        }

        return pieData;
    }

    /**
     * API pour récupérer l'activité récente de l'utilisateur
     */
    @GetMapping("/api/dashboard/recent-activity")
    public List<Map<String, Object>> getRecentActivity() {
        String currentUser = getCurrentUsername();
        logger.debug("Récupération de l'activité récente pour l'utilisateur: {}", currentUser);

        List<Map<String, Object>> activities = new ArrayList<>();

        try {
            if (mt103Repository != null) {
                // Récupérer les 3 dernières conversions de l'utilisateur
                List<com.attijari.MT103converter.models.MT103Msg> recentMessages =
                    mt103Repository.findTop3ByUsernameOrderByCreatedAtDesc(currentUser);

                for (com.attijari.MT103converter.models.MT103Msg message : recentMessages) {
                    Map<String, Object> activity = new HashMap<>();

                    // Déterminer si c'est un succès ou une erreur
                    boolean isSuccess = message.getPacs008Xml() != null &&
                                      message.getPacs008Xml().trim().length() > 50;

                    activity.put("type", isSuccess ? "success" : "error");
                    activity.put("title", isSuccess ? "Conversion réussie" : "Échec de conversion");

                    // Créer la description
                    StringBuilder description = new StringBuilder();
                    description.append("MT103 → PACS008");

                    // Ajouter le montant si disponible
                    try {
                        String field32A = message.getField("32A");
                        if (field32A != null && field32A.length() > 9) {
                            String currency = field32A.substring(6, 9);
                            String amountStr = field32A.substring(9).replace(",", ".");
                            description.append(" | Montant: ").append(amountStr).append(" ").append(currency);
                        }
                    } catch (Exception e) {
                        // Ignorer les erreurs d'extraction du montant
                    }

                    // Ajouter la référence de transaction si disponible
                    try {
                        String transactionRef = message.getField("20");
                        if (transactionRef != null && !transactionRef.trim().isEmpty()) {
                            description.append(" | Réf: ").append(transactionRef.trim().substring(0, Math.min(10, transactionRef.trim().length())));
                        }
                    } catch (Exception e) {
                        // Ignorer les erreurs d'extraction de référence
                    }

                    activity.put("description", description.toString());

                    // Calculer le temps relatif
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime createdAt = message.getCreatedAt();
                    long minutesAgo = java.time.Duration.between(createdAt, now).toMinutes();

                    String timeAgo;
                    if (minutesAgo < 1) {
                        timeAgo = "À l'instant";
                    } else if (minutesAgo < 60) {
                        timeAgo = "Il y a " + minutesAgo + " min";
                    } else if (minutesAgo < 1440) {
                        long hoursAgo = minutesAgo / 60;
                        timeAgo = "Il y a " + hoursAgo + "h";
                    } else {
                        long daysAgo = minutesAgo / 1440;
                        timeAgo = "Il y a " + daysAgo + "j";
                    }

                    activity.put("time", timeAgo);
                    activities.add(activity);
                }

                logger.debug("Activité récente générée pour {}: {} éléments", currentUser, activities.size());
            } else {
                logger.warn("Repository MT103 est null, pas d'activité récente disponible");
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération de l'activité récente pour {}: {}", currentUser, e.getMessage());
        }

        return activities;
    }

    // === Méthodes pour récupérer l'utilisateur connecté ===

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

    // === Méthodes utilitaires pour les statistiques FILTRÉES PAR UTILISATEUR ===

    private long getTotalConversions(String username) {
        try {
            return mt103Repository != null ? mt103Repository.countByUsername(username) : 0;
        } catch (Exception e) {
            logger.warn("Impossible de récupérer le nombre total de conversions pour {}: {}", username, e.getMessage());
            return 0;
        }
    }

    private double getSuccessRate(String username) {
        try {
            if (mt103Repository == null) return 0.0;

            long total = mt103Repository.countByUsername(username);
            if (total == 0) return 0.0;

            // Compter les conversions réussies pour cet utilisateur (pacs008Xml != null && length > 50)
            List<com.attijari.MT103converter.models.MT103Msg> messages = mt103Repository.findTop50ByUsernameOrderByCreatedAtDesc(username);
            long successful = messages.stream()
                .filter(msg -> msg.getPacs008Xml() != null && msg.getPacs008Xml().trim().length() > 50)
                .count();

            return (double) successful / total * 100.0;
        } catch (Exception e) {
            logger.warn("Impossible de calculer le taux de succès pour {}: {}", username, e.getMessage());
            return 0.0;
        }
    }

    private long getTodayConversions(String username) {
        try {
            if (mt103Repository == null) return 0;

            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            return mt103Repository.countByUsernameAndCreatedAtBetween(username, startOfDay, endOfDay);
        } catch (Exception e) {
            logger.warn("Impossible de récupérer les conversions du jour pour {}: {}", username, e.getMessage());
            return 0;
        }
    }

    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"));
    }

    private List<Map<String, Object>> getRealTimelineData(String username) {
        List<Map<String, Object>> timeline = new ArrayList<>();

        try {
            if (mt103Repository == null) {
                logger.warn("Repository MT103 est null, utilisation des données factices");
                return generateMockTimelineData(); // Fallback si pas de DB
            }

            // Générer les statistiques pour les 7 derniers jours
            for (int i = 6; i >= 0; i--) {
                LocalDateTime date = LocalDateTime.now().minusDays(i);
                LocalDateTime startOfDay = date.withHour(0).withMinute(0).withSecond(0).withNano(0);
                LocalDateTime endOfDay = startOfDay.plusDays(1);

                Map<String, Object> dayData = new HashMap<>();

                // Total des conversions ce jour-là
                long totalConversions = mt103Repository.countByUsernameAndCreatedAtBetween(username, startOfDay, endOfDay);

                // Conversions réussies avec la même logique que l'historique (pacs008Xml != null && length > 50)
                List<com.attijari.MT103converter.models.MT103Msg> dayMessages = mt103Repository.findByUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(username, startOfDay, endOfDay);
                long successfulConversions = dayMessages.stream()
                    .filter(msg -> msg.getPacs008Xml() != null && msg.getPacs008Xml().trim().length() > 50)
                    .count();

                // Conversions en erreur
                long errorConversions = totalConversions - successfulConversions;

                // LOGS DE DEBUG pour voir les vraies données
                logger.info("Date: {} - Total: {}, Succès: {}, Erreurs: {}",
                    date.format(DateTimeFormatter.ofPattern("dd/MM")),
                    totalConversions, successfulConversions, errorConversions);

                dayData.put("date", date.format(DateTimeFormatter.ofPattern("dd/MM")));
                dayData.put("conversions", totalConversions);
                dayData.put("success", successfulConversions);
                dayData.put("errors", errorConversions);

                timeline.add(dayData);
            }

            // Log final pour debug
            logger.info("Timeline générée avec {} jours de données", timeline.size());

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des données temporelles: {}", e.getMessage());
            return generateMockTimelineData(); // Fallback en cas d'erreur
        }

        return timeline;
    }

    private List<Map<String, Object>> generateMockTimelineData() {
        List<Map<String, Object>> timeline = new ArrayList<>();
        Random random = new Random();

        // Simulation des 7 derniers jours
        for (int i = 6; i >= 0; i--) {
            Map<String, Object> dayData = new HashMap<>();
            LocalDateTime date = LocalDateTime.now().minusDays(i);

            dayData.put("date", date.format(DateTimeFormatter.ofPattern("dd/MM")));
            dayData.put("conversions", random.nextInt(40) + 10); // 10-50 conversions
            dayData.put("success", random.nextInt(35) + 15); // 15-50 succès
            dayData.put("errors", random.nextInt(5) + 1); // 1-6 erreurs

            timeline.add(dayData);
        }

        return timeline;
    }
}
