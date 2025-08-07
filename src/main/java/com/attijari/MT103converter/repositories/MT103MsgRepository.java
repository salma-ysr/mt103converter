package com.attijari.MT103converter.repositories;

import com.attijari.MT103converter.models.MT103Msg;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface MT103MsgRepository extends MongoRepository<MT103Msg, String> {

    // Compter les conversions réussies (avec XML généré)
    long countByPacs008XmlIsNotNull();

    // Compter les conversions dans une période donnée
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Compter les conversions réussies dans une période donnée
    long countByCreatedAtBetweenAndPacs008XmlIsNotNull(LocalDateTime start, LocalDateTime end);

    // Récupérer les conversions d'une période pour l'historique
    List<MT103Msg> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    // Récupérer les dernières conversions pour l'historique (avec pagination)
    List<MT103Msg> findTop50ByOrderByCreatedAtDesc();

    // Nouvelles méthodes pour le filtrage par utilisateur
    long countByUsername(String username);
    long countByUsernameAndPacs008XmlIsNotNull(String username);
    long countByUsernameAndCreatedAtBetween(String username, LocalDateTime start, LocalDateTime end);
    long countByUsernameAndCreatedAtBetweenAndPacs008XmlIsNotNull(String username, LocalDateTime start, LocalDateTime end);
    List<MT103Msg> findByUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(String username, LocalDateTime start, LocalDateTime end);
    List<MT103Msg> findTop50ByUsernameOrderByCreatedAtDesc(String username);
    List<MT103Msg> findTop10ByUsernameOrderByCreatedAtDesc(String username);
    List<MT103Msg> findTop3ByUsernameOrderByCreatedAtDesc(String username);
}
