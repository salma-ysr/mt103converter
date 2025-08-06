package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.repositories.MT103MsgRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private static final Logger logger = LogManager.getLogger(DebugController.class);

    @Autowired(required = false)
    private MT103MsgRepository repository;

    @GetMapping("/check-database")
    public Map<String, Object> checkDatabase() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (repository == null) {
                response.put("error", "Repository is null");
                return response;
            }

            List<MT103Msg> allMessages = repository.findAll();
            response.put("totalMessages", allMessages.size());

            int successCount = 0;
            int errorCount = 0;

            for (MT103Msg msg : allMessages) {
                logger.info("DEBUG - Message ID: {}, Username: {}, XML Present: {}, XML Length: {}",
                          msg.getId(),
                          msg.getUsername(),
                          msg.getPacs008Xml() != null,
                          msg.getPacs008Xml() != null ? msg.getPacs008Xml().length() : 0);

                if (msg.getPacs008Xml() != null && !msg.getPacs008Xml().trim().isEmpty()) {
                    successCount++;
                } else {
                    errorCount++;
                }
            }

            response.put("successCount", successCount);
            response.put("errorCount", errorCount);
            response.put("messages", allMessages.stream().map(msg -> {
                Map<String, Object> msgInfo = new HashMap<>();
                msgInfo.put("id", msg.getId());
                msgInfo.put("username", msg.getUsername());
                msgInfo.put("hasXml", msg.getPacs008Xml() != null);
                msgInfo.put("xmlLength", msg.getPacs008Xml() != null ? msg.getPacs008Xml().length() : 0);
                msgInfo.put("transactionId", msg.getField("20"));
                msgInfo.put("xmlPreview", msg.getPacs008Xml() != null ?
                    msg.getPacs008Xml().substring(0, Math.min(100, msg.getPacs008Xml().length())) : "NULL");
                return msgInfo;
            }).toList());

        } catch (Exception e) {
            logger.error("Erreur lors de la vérification de la base: {}", e.getMessage());
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/test-historique")
    public Map<String, Object> testHistorique() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (repository == null) {
                response.put("error", "Repository is null");
                return response;
            }

            // Test direct avec un utilisateur spécifique
            String testUser = "user1"; // Remplacez par un utilisateur que vous utilisez

            List<MT103Msg> messagesForUser = repository.findTop50ByUsernameOrderByCreatedAtDesc(testUser);
            response.put("testUser", testUser);
            response.put("messagesForTestUser", messagesForUser.size());

            // Test avec tous les messages
            List<MT103Msg> allMessages = repository.findAll();
            response.put("totalMessages", allMessages.size());

            // Analyser chaque message pour cet utilisateur
            List<Map<String, Object>> userMessages = new ArrayList<>();
            for (MT103Msg msg : messagesForUser) {
                Map<String, Object> msgInfo = new HashMap<>();
                msgInfo.put("id", msg.getId());
                msgInfo.put("username", msg.getUsername());
                msgInfo.put("transactionId", msg.getField("20"));

                // Reproduire exactement la logique de l'historique
                boolean isSuccess = msg.getPacs008Xml() != null &&
                                  msg.getPacs008Xml().trim().length() > 50;

                msgInfo.put("xmlPresent", msg.getPacs008Xml() != null);
                msgInfo.put("xmlLength", msg.getPacs008Xml() != null ? msg.getPacs008Xml().length() : 0);
                msgInfo.put("xmlTrimmedLength", msg.getPacs008Xml() != null ? msg.getPacs008Xml().trim().length() : 0);
                msgInfo.put("calculatedStatus", isSuccess ? "Succès" : "Erreur");

                userMessages.add(msgInfo);
            }

            response.put("userMessagesDetails", userMessages);

        } catch (Exception e) {
            logger.error("Erreur lors du test historique: {}", e.getMessage());
            response.put("error", e.getMessage());
        }

        return response;
    }
}
