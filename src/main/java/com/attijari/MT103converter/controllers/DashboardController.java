package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.repositories.MT103MsgRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
     * API pour récupérer les statistiques du dashboard
     */
    @GetMapping("/api/dashboard/stats")
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConversions", getTotalConversions());
        stats.put("successRate", getSuccessRate());
        stats.put("todayConversions", getTodayConversions());
        stats.put("lastUpdate", getCurrentDateTime());
        return stats;
    }

    /**
     * API pour les données des graphiques
     */
    @GetMapping("/api/dashboard/chart-data")
    public List<Map<String, Object>> getChartData() {
        logger.debug("Récupération des données de graphiques en temps réel");
        return getRealTimelineData();
    }

    // === Méthodes utilitaires pour les statistiques RÉELLES ===

    private long getTotalConversions() {
        try {
            return mt103Repository != null ? mt103Repository.count() : 0;
        } catch (Exception e) {
            logger.warn("Impossible de récupérer le nombre total de conversions: {}", e.getMessage());
            return 0;
        }
    }

    private double getSuccessRate() {
        try {
            if (mt103Repository == null) return 0.0;

            long total = mt103Repository.count();
            if (total == 0) return 0.0;

            // Compter les conversions réussies (celles qui ont un XML généré)
            long successful = mt103Repository.countByPacs008XmlIsNotNull();

            return (double) successful / total * 100.0;
        } catch (Exception e) {
            logger.warn("Impossible de calculer le taux de succès: {}", e.getMessage());
            return 0.0;
        }
    }

    private long getTodayConversions() {
        try {
            if (mt103Repository == null) return 0;

            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            return mt103Repository.countByCreatedAtBetween(startOfDay, endOfDay);
        } catch (Exception e) {
            logger.warn("Impossible de récupérer les conversions du jour: {}", e.getMessage());
            return 0;
        }
    }

    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"));
    }

    private List<Map<String, Object>> getRealTimelineData() {
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
                long totalConversions = mt103Repository.countByCreatedAtBetween(startOfDay, endOfDay);

                // Conversions réussies (avec XML généré et non vide)
                long successfulConversions = mt103Repository.countByCreatedAtBetweenAndPacs008XmlIsNotNull(startOfDay, endOfDay);

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
