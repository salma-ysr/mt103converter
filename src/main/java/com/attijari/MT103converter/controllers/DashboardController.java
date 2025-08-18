package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.repositories.MT103MsgRepository;
import com.attijari.MT103converter.repositories.Pacs008ToMT103ConversionRepository;
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

    @Autowired(required = false)
    private Pacs008ToMT103ConversionRepository reverseRepository;

    /**
     * API pour récupérer les statistiques du dashboard par utilisateur
     */
    @GetMapping("/api/dashboard/stats")
    public Map<String, Object> getDashboardStats() {
        String currentUser = getCurrentUsername();
        logger.info("Récupération des statistiques dashboard pour l'utilisateur: {}", currentUser);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConversions", getTotalConversions(currentUser));
        stats.put("successRate", getSuccessRate(currentUser));
        stats.put("todayConversions", getTodayConversions(currentUser));
        stats.put("lastUpdate", getCurrentDateTime());
        stats.put("currentUser", currentUser);
        
        // Ajouter les trends réels
        Map<String, Object> trends = calculateRealTrends(currentUser);
        stats.putAll(trends);

        logger.info("Statistiques générées: {}", stats);
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
            long totalSuccess = 0;
            long totalErrors = 0;
            long totalConversions = 0;

            // Compter les conversions MT103→PACS008
            if (mt103Repository != null) {
                List<com.attijari.MT103converter.models.MT103Msg> messages =
                    mt103Repository.findTop50ByUsernameOrderByCreatedAtDesc(currentUser);

                long mt103Success = messages.stream()
                    .filter(msg -> msg.getPacs008Xml() != null && msg.getPacs008Xml().trim().length() > 50)
                    .count();

                long mt103Total = messages.size();
                totalSuccess += mt103Success;
                totalErrors += (mt103Total - mt103Success);
                totalConversions += mt103Total;
            }

            // Compter les conversions PACS008→MT103
            if (reverseRepository != null) {
                List<com.attijari.MT103converter.models.Pacs008ToMT103Conversion> reverseMessages =
                    reverseRepository.findTop50ByUsernameOrderByCreatedAtDesc(currentUser);

                long pacsSuccess = reverseMessages.stream()
                    .filter(com.attijari.MT103converter.models.Pacs008ToMT103Conversion::isSuccess)
                    .count();

                long pacsTotal = reverseMessages.size();
                totalSuccess += pacsSuccess;
                totalErrors += (pacsTotal - pacsSuccess);
                totalConversions += pacsTotal;
            }

            pieData.put("success", totalSuccess);
            pieData.put("errors", totalErrors);
            pieData.put("total", totalConversions);

            logger.debug("Données pie chart pour {}: {} succès, {} erreurs sur {} total",
                currentUser, totalSuccess, totalErrors, totalConversions);

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
     * API pour récupérer l'activité récente de l'utilisateur (TOUTES les conversions)
     */
    @GetMapping("/api/dashboard/recent-activity")
    public List<Map<String, Object>> getRecentActivity() {
        String currentUser = getCurrentUsername();
        logger.debug("Récupération de l'activité récente pour l'utilisateur: {}", currentUser);

        List<Map<String, Object>> activities = new ArrayList<>();

        try {
            // Récupérer les conversions MT103 → PACS008
            if (mt103Repository != null) {
                List<com.attijari.MT103converter.models.MT103Msg> mt103Messages =
                    mt103Repository.findTop5ByUsernameOrderByCreatedAtDesc(currentUser);

                for (com.attijari.MT103converter.models.MT103Msg message : mt103Messages) {
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
                            String trimmedRef = transactionRef.trim();
                            description.append(" | Réf: ").append(trimmedRef.substring(0, Math.min(10, trimmedRef.length())));
                        }
                    } catch (Exception e) {
                        // Ignorer les erreurs d'extraction de référence
                    }

                    activity.put("description", description.toString());
                    activity.put("createdAt", message.getCreatedAt()); // Pour le tri
                    activity.put("time", formatTimeAgo(message.getCreatedAt()));

                    activities.add(activity);
                }
            }

            // Récupérer les conversions PACS008 → MT103
            if (reverseRepository != null) {
                List<com.attijari.MT103converter.models.Pacs008ToMT103Conversion> pacsMessages =
                    reverseRepository.findTop5ByUsernameOrderByCreatedAtDesc(currentUser);

                for (com.attijari.MT103converter.models.Pacs008ToMT103Conversion conversion : pacsMessages) {
                    Map<String, Object> activity = new HashMap<>();

                    boolean isSuccess = conversion.isSuccess();
                    activity.put("type", isSuccess ? "success" : "error");
                    activity.put("title", isSuccess ? "Conversion réussie" : "Échec de conversion");

                    // Créer la description pour PACS008 → MT103
                    StringBuilder description = new StringBuilder();
                    description.append("PACS008 → MT103");

                    // Ajouter des détails si disponibles
                    if (conversion.getMt103Result() != null && !conversion.getMt103Result().trim().isEmpty()) {
                        try {
                            // Essayer d'extraire des informations du MT103 généré
                            String mt103Output = conversion.getMt103Result();
                            if (mt103Output.contains(":32A:")) {
                                int start = mt103Output.indexOf(":32A:") + 5;
                                int end = mt103Output.indexOf("\n", start);
                                if (end > start && end < start + 50) {
                                    String field32A = mt103Output.substring(start, end).trim();
                                    if (field32A.length() > 9) {
                                        String currency = field32A.substring(6, 9);
                                        String amountStr = field32A.substring(9).replace(",", ".");
                                        description.append(" | Montant: ").append(amountStr).append(" ").append(currency);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Ignorer les erreurs d'extraction
                        }
                    }

                    // Ajouter le message d'erreur si échec
                    if (!isSuccess && conversion.getErrorMessage() != null && !conversion.getErrorMessage().trim().isEmpty()) {
                        String errorMsg = conversion.getErrorMessage().trim();
                        description.append(" | ").append(errorMsg.substring(0, Math.min(30, errorMsg.length())));
                        if (errorMsg.length() > 30) {
                            description.append("...");
                        }
                    }

                    activity.put("description", description.toString());
                    activity.put("createdAt", conversion.getCreatedAt()); // Pour le tri
                    activity.put("time", formatTimeAgo(conversion.getCreatedAt()));

                    activities.add(activity);
                }
            }

            // Trier toutes les activités par date (plus récentes en premier) et limiter à 5
            activities.sort((a, b) -> {
                LocalDateTime dateA = (LocalDateTime) a.get("createdAt");
                LocalDateTime dateB = (LocalDateTime) b.get("createdAt");
                return dateB.compareTo(dateA); // Ordre décroissant (plus récent d'abord)
            });

            // Garder seulement les 5 plus récentes et supprimer le champ createdAt
            activities = activities.stream()
                .limit(5)
                .peek(activity -> activity.remove("createdAt"))
                .collect(java.util.stream.Collectors.toList());

            logger.debug("Activités récentes générées pour {}: {} éléments (MT103→PACS008 + PACS008→MT103)",
                currentUser, activities.size());

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération de l'activité récente pour {}: {}", currentUser, e.getMessage());
        }

        return activities;
    }

    /**
     * API pour récupérer les informations de l'utilisateur connecté
     */
    @GetMapping("/api/user/info")
    public Map<String, Object> getUserInfo() {
        Map<String, Object> userInfo = new HashMap<>();

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String username = getCurrentUsername();
                userInfo.put("username", username);

                // Si c'est un utilisateur OIDC (Keycloak), récupérer plus d'informations
                if (authentication.getPrincipal() instanceof OidcUser) {
                    OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
                    userInfo.put("displayName", oidcUser.getFullName() != null ? oidcUser.getFullName() : username);
                    userInfo.put("email", oidcUser.getEmail());
                    userInfo.put("givenName", oidcUser.getGivenName());
                    userInfo.put("familyName", oidcUser.getFamilyName());
                } else {
                    userInfo.put("displayName", username);
                }

                userInfo.put("authenticated", true);
                logger.debug("Informations utilisateur récupérées pour: {}", username);
            } else {
                userInfo.put("username", "anonymous");
                userInfo.put("displayName", "Utilisateur");
                userInfo.put("authenticated", false);
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des informations utilisateur: {}", e.getMessage());
            userInfo.put("username", "anonymous");
            userInfo.put("displayName", "Utilisateur");
            userInfo.put("authenticated", false);
        }

        return userInfo;
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
            long mt103Count = mt103Repository != null ? mt103Repository.countByUsername(username) : 0;
            long pacsCount = reverseRepository != null ? reverseRepository.countByUsername(username) : 0;
            return mt103Count + pacsCount;
        } catch (Exception e) {
            logger.warn("Impossible de récupérer le nombre total de conversions pour {}: {}", username, e.getMessage());
            return 0;
        }
    }

    private double getSuccessRate(String username) {
        try {
            if (mt103Repository == null) return 0.0;

            // Compter les conversions MT103→PACS008
            List<com.attijari.MT103converter.models.MT103Msg> messages = mt103Repository.findTop50ByUsernameOrderByCreatedAtDesc(username);
            long totalMT103 = messages.size();
            long successfulMT103 = messages.stream()
                .filter(msg -> msg.getPacs008Xml() != null && msg.getPacs008Xml().trim().length() > 50)
                .count();

            // Compter les conversions PACS008→MT103 (utiliser success au lieu de isSuccess)
            long totalPacs = 0;
            long successfulPacs = 0;
            if (reverseRepository != null) {
                List<com.attijari.MT103converter.models.Pacs008ToMT103Conversion> reverseMessages =
                    reverseRepository.findTop50ByUsernameOrderByCreatedAtDesc(username);
                totalPacs = reverseMessages.size();
                successfulPacs = reverseMessages.stream()
                    .filter(msg -> msg.isSuccess())
                    .count();
            }

            long totalConversions = totalMT103 + totalPacs;
            if (totalConversions == 0) return 0.0;

            long totalSuccessful = successfulMT103 + successfulPacs;
            return (double) totalSuccessful / totalConversions * 100.0;
        } catch (Exception e) {
            logger.warn("Impossible de calculer le taux de succès pour {}: {}", username, e.getMessage());
            return 0.0;
        }
    }

    private long getTodayConversions(String username) {
        try {
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            long mt103Count = 0;
            long pacsCount = 0;

            // Compter les conversions MT103→PACS008 d'aujourd'hui
            if (mt103Repository != null) {
                mt103Count = mt103Repository.countByUsernameAndCreatedAtBetween(username, startOfDay, endOfDay);
            }

            // Compter les conversions PACS008→MT103 d'aujourd'hui
            if (reverseRepository != null) {
                pacsCount = reverseRepository.countByUsernameAndCreatedAtBetween(username, startOfDay, endOfDay);
            }

            return mt103Count + pacsCount;
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

                // === Conversions MT103→PACS008 ===
                long mt103TotalConversions = mt103Repository.countByUsernameAndCreatedAtBetween(username, startOfDay, endOfDay);

                List<com.attijari.MT103converter.models.MT103Msg> mt103DayMessages =
                    mt103Repository.findByUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(username, startOfDay, endOfDay);
                long mt103SuccessfulConversions = mt103DayMessages.stream()
                    .filter(msg -> msg.getPacs008Xml() != null && msg.getPacs008Xml().trim().length() > 50)
                    .count();
                long mt103ErrorConversions = mt103TotalConversions - mt103SuccessfulConversions;

                // === Conversions PACS008→MT103 ===
                long pacsTotalConversions = 0;
                long pacsSuccessfulConversions = 0;
                long pacsErrorConversions = 0;

                if (reverseRepository != null) {
                    pacsTotalConversions = reverseRepository.countByUsernameAndCreatedAtBetween(username, startOfDay, endOfDay);

                    List<com.attijari.MT103converter.models.Pacs008ToMT103Conversion> pacsDayMessages =
                        reverseRepository.findByUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(username, startOfDay, endOfDay);
                    pacsSuccessfulConversions = pacsDayMessages.stream()
                        .filter(conversion -> conversion.isSuccess())
                        .count();
                    pacsErrorConversions = pacsTotalConversions - pacsSuccessfulConversions;
                }

                // === TOTAUX COMBINÉS ===
                long totalConversions = mt103TotalConversions + pacsTotalConversions;
                long totalSuccessfulConversions = mt103SuccessfulConversions + pacsSuccessfulConversions;
                long totalErrorConversions = mt103ErrorConversions + pacsErrorConversions;

                // LOGS DE DEBUG pour voir les vraies données
                logger.info("Date: {} - MT103→PACS008: {}/{} succès, PACS008→MT103: {}/{} succès, TOTAL: {}/{} succès",
                    date.format(DateTimeFormatter.ofPattern("dd/MM")),
                    mt103SuccessfulConversions, mt103TotalConversions,
                    pacsSuccessfulConversions, pacsTotalConversions,
                    totalSuccessfulConversions, totalConversions);

                dayData.put("date", date.format(DateTimeFormatter.ofPattern("dd/MM")));
                dayData.put("conversions", totalConversions);
                dayData.put("success", totalSuccessfulConversions);
                dayData.put("errors", totalErrorConversions);

                timeline.add(dayData);
            }

            // Log final pour debug
            logger.info("Timeline générée avec {} jours de données (MT103→PACS008 + PACS008→MT103)", timeline.size());

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

    // === Méthodes pour calculer les tendances réelles ===

    /**
     * Calcule les tendances réelles basées sur les données historiques
     */
    private Map<String, Object> calculateRealTrends(String username) {
        Map<String, Object> trends = new HashMap<>();

        try {
            // Calculer la tendance totale (comparaison cette semaine vs semaine dernière)
            Map<String, Object> totalTrend = calculateTotalTrend(username);
            trends.putAll(totalTrend);

            // Calculer la tendance du taux de succès
            Map<String, Object> successTrend = calculateSuccessTrend(username);
            trends.putAll(successTrend);

            // Calculer la tendance des conversions d'aujourd'hui vs hier
            Map<String, Object> todayTrend = calculateTodayTrend(username);
            trends.putAll(todayTrend);

            logger.info("Tendances réelles calculées pour {}: {}", username, trends);

        } catch (Exception e) {
            logger.error("Erreur lors du calcul des tendances réelles pour {}: {}", username, e.getMessage());
            // Valeurs par défaut en cas d'erreur
            trends.put("totalTrend", "Stable");
            trends.put("totalTrendValue", 0.0);
            trends.put("totalTrendClass", "trend-stable");
            trends.put("successTrend", "Stable");
            trends.put("successTrendValue", 0.0);
            trends.put("successTrendClass", "trend-stable");
            trends.put("todayTrend", "Stable");
            trends.put("todayTrendValue", 0.0);
            trends.put("todayTrendClass", "trend-stable");
        }

        return trends;
    }

    /**
     * Calcule la tendance totale (cette semaine vs semaine dernière)
     */
    private Map<String, Object> calculateTotalTrend(String username) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (mt103Repository == null) {
                return getDefaultTrend("totalTrend");
            }

            LocalDateTime now = LocalDateTime.now();

            // Cette semaine (7 derniers jours)
            LocalDateTime startThisWeek = now.minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endThisWeek = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            // Semaine dernière (jours 7 à 13)
            LocalDateTime startLastWeek = now.minusDays(13).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endLastWeek = now.minusDays(7).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            long thisWeekCount = mt103Repository.countByUsernameAndCreatedAtBetween(username, startThisWeek, endThisWeek);
            long lastWeekCount = mt103Repository.countByUsernameAndCreatedAtBetween(username, startLastWeek, endLastWeek);

            // Inclure les conversions inverses si disponibles
            if (reverseRepository != null) {
                thisWeekCount += reverseRepository.countByUsernameAndCreatedAtBetween(username, startThisWeek, endThisWeek);
                lastWeekCount += reverseRepository.countByUsernameAndCreatedAtBetween(username, startLastWeek, endLastWeek);
            }

            double variation = calculatePercentageVariation(thisWeekCount, lastWeekCount);
            String trendText = formatTrendText(variation);
            String trendClass = getTrendClass(variation);

            result.put("totalTrend", trendText);
            result.put("totalTrendValue", variation);
            result.put("totalTrendClass", trendClass);

            logger.debug("Tendance totale calculée pour {}: cette semaine={}, semaine dernière={}, variation={}%",
                username, thisWeekCount, lastWeekCount, variation);

        } catch (Exception e) {
            logger.error("Erreur calcul tendance totale: {}", e.getMessage());
            return getDefaultTrend("totalTrend");
        }

        return result;
    }

    /**
     * Calcule la tendance du taux de succès
     */
    private Map<String, Object> calculateSuccessTrend(String username) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (mt103Repository == null) {
                return getDefaultTrend("successTrend");
            }

            LocalDateTime now = LocalDateTime.now();

            // Cette semaine
            LocalDateTime startThisWeek = now.minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endThisWeek = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            // Semaine dernière
            LocalDateTime startLastWeek = now.minusDays(13).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endLastWeek = now.minusDays(7).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            // Calculer le taux de succès cette semaine
            List<com.attijari.MT103converter.models.MT103Msg> thisWeekMessages =
                mt103Repository.findByUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(username, startThisWeek, endThisWeek);
            long thisWeekSuccess = thisWeekMessages.stream()
                .filter(msg -> msg.getPacs008Xml() != null && msg.getPacs008Xml().trim().length() > 50)
                .count();
            double thisWeekRate = thisWeekMessages.isEmpty() ? 0.0 : (double) thisWeekSuccess / thisWeekMessages.size() * 100.0;

            // Calculer le taux de succès semaine dernière
            List<com.attijari.MT103converter.models.MT103Msg> lastWeekMessages =
                mt103Repository.findByUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(username, startLastWeek, endLastWeek);
            long lastWeekSuccess = lastWeekMessages.stream()
                .filter(msg -> msg.getPacs008Xml() != null && msg.getPacs008Xml().trim().length() > 50)
                .count();
            double lastWeekRate = lastWeekMessages.isEmpty() ? 0.0 : (double) lastWeekSuccess / lastWeekMessages.size() * 100.0;

            double variation = thisWeekRate - lastWeekRate;
            String trendText = formatSuccessRateTrend(variation);
            String trendClass = getTrendClass(variation);

            result.put("successTrend", trendText);
            result.put("successTrendValue", variation);
            result.put("successTrendClass", trendClass);

            logger.debug("Tendance taux succès calculée pour {}: cette semaine={}%, semaine dernière={}%, variation={}%",
                username, thisWeekRate, lastWeekRate, variation);

        } catch (Exception e) {
            logger.error("Erreur calcul tendance succès: {}", e.getMessage());
            return getDefaultTrend("successTrend");
        }

        return result;
    }

    /**
     * Calcule la tendance aujourd'hui vs hier
     */
    private Map<String, Object> calculateTodayTrend(String username) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (mt103Repository == null) {
                return getDefaultTrend("todayTrend");
            }

            LocalDateTime now = LocalDateTime.now();

            // Aujourd'hui
            LocalDateTime startToday = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endToday = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            // Hier
            LocalDateTime startYesterday = now.minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endYesterday = now.minusDays(1).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            long todayCount = mt103Repository.countByUsernameAndCreatedAtBetween(username, startToday, endToday);
            long yesterdayCount = mt103Repository.countByUsernameAndCreatedAtBetween(username, startYesterday, endYesterday);

            // Inclure les conversions inverses si disponibles
            if (reverseRepository != null) {
                todayCount += reverseRepository.countByUsernameAndCreatedAtBetween(username, startToday, endToday);
                yesterdayCount += reverseRepository.countByUsernameAndCreatedAtBetween(username, startYesterday, endYesterday);
            }

            double variation = calculatePercentageVariation(todayCount, yesterdayCount);
            String trendText = formatTodayTrend(variation, todayCount, yesterdayCount);
            String trendClass = getTrendClass(variation);

            result.put("todayTrend", trendText);
            result.put("todayTrendValue", variation);
            result.put("todayTrendClass", trendClass);

            logger.debug("Tendance aujourd'hui calculée pour {}: aujourd'hui={}, hier={}, variation={}%",
                username, todayCount, yesterdayCount, variation);

        } catch (Exception e) {
            logger.error("Erreur calcul tendance aujourd'hui: {}", e.getMessage());
            return getDefaultTrend("todayTrend");
        }

        return result;
    }

    // === Méthodes utilitaires pour les tendances ===

    private double calculatePercentageVariation(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return ((double) (current - previous) / previous) * 100.0;
    }

    private String formatTrendText(double variation) {
        if (Math.abs(variation) < 1.0) {
            return "Stable";
        } else if (variation > 0) {
            return String.format("+%.1f%%", variation);
        } else {
            return String.format("%.1f%%", variation);
        }
    }

    private String formatSuccessRateTrend(double variation) {
        if (Math.abs(variation) < 1.0) {
            return "Stable";
        } else if (variation > 0) {
            return String.format("+%.1f pts", variation);
        } else {
            return String.format("%.1f pts", variation);
        }
    }

    private String formatTodayTrend(double variation, long today, long yesterday) {
        if (today == 0 && yesterday == 0) {
            return "Aucune";
        } else if (Math.abs(variation) < 5.0) {
            return "Stable";
        } else if (variation > 0) {
            return String.format("+%.0f%%", variation);
        } else {
            return String.format("%.0f%%", variation);
        }
    }

    private String getTrendClass(double variation) {
        if (Math.abs(variation) < 1.0) {
            return "trend-stable";
        } else if (variation > 0) {
            return "trend-up";
        } else {
            return "trend-down";
        }
    }

    private Map<String, Object> getDefaultTrend(String prefix) {
        Map<String, Object> result = new HashMap<>();
        result.put(prefix, "Stable");
        result.put(prefix + "Value", 0.0);
        result.put(prefix + "Class", "trend-stable");
        return result;
    }

    /**
     * Méthode utilitaire pour formater le temps relatif
     */
    private String formatTimeAgo(LocalDateTime createdAt) {
        LocalDateTime now = LocalDateTime.now();
        long minutesAgo = java.time.Duration.between(createdAt, now).toMinutes();

        if (minutesAgo < 1) {
            return "À l'instant";
        } else if (minutesAgo < 60) {
            return "Il y a " + minutesAgo + " min";
        } else if (minutesAgo < 1440) {
            long hoursAgo = minutesAgo / 60;
            return "Il y a " + hoursAgo + "h";
        } else {
            long daysAgo = minutesAgo / 1440;
            return "Il y a " + daysAgo + "j";
        }
    }
}
