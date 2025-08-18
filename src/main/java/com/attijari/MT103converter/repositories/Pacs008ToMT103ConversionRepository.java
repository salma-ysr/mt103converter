package com.attijari.MT103converter.repositories;

import com.attijari.MT103converter.models.Pacs008ToMT103Conversion;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface Pacs008ToMT103ConversionRepository extends MongoRepository<Pacs008ToMT103Conversion, String> {
    // Méthodes existantes
    long countByUsername(String username);
    List<Pacs008ToMT103Conversion> findTop50ByUsernameOrderByCreatedAtDesc(String username);

    // Nouvelles méthodes pour les statistiques du dashboard
    long countByUsernameAndSuccess(String username, boolean success);
    long countByUsernameAndCreatedAtBetween(String username, LocalDateTime start, LocalDateTime end);
    long countByUsernameAndCreatedAtBetweenAndSuccess(String username, LocalDateTime start, LocalDateTime end, boolean success);
    List<Pacs008ToMT103Conversion> findByUsernameAndCreatedAtBetweenOrderByCreatedAtDesc(String username, LocalDateTime start, LocalDateTime end);
    List<Pacs008ToMT103Conversion> findTop3ByUsernameOrderByCreatedAtDesc(String username);
    List<Pacs008ToMT103Conversion> findTop5ByUsernameOrderByCreatedAtDesc(String username); // MÉTHODE MANQUANTE AJOUTÉE
}
