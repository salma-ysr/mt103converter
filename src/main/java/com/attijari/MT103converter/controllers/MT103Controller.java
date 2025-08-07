package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.converters.MT103ToPacs008Converter;
import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.repositories.MT103MsgRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
public class MT103Controller {
    private static final Logger logger = LogManager.getLogger(MT103Controller.class);

    private final MT103ToPacs008Converter converter;
    private final MT103MsgRepository repository;

    // Stockage temporaire du dernier fichier converti
    private static String lastConvertedXml = null;
    private static String lastConvertedFilename = null;

    public MT103Controller(MT103ToPacs008Converter converter, MT103MsgRepository repository) {
        this.converter = converter;
        this.repository = repository;
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

    @PostMapping(value = "/convert", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConversionResponse> convertMt103(@RequestBody String rawMt103) {
        String currentUser = getCurrentUsername();
        logger.info("Received MT103 file for conversion from user: {}", currentUser);

        MT103ToPacs008Converter.ConversionResult result = converter.process(rawMt103);

        // *** MODIFICATION : Sauvegarder TOUTES les conversions avec le nom d'utilisateur ***
        try {
            // Créer l'objet MT103Msg pour toutes les conversions
            MT103Msg mt103Msg = result.getMt103Msg();

            // **IMPORTANT : Enregistrer le nom d'utilisateur**
            mt103Msg.setUsername(currentUser);

            if (result.isSuccess()) {
                // Pour les succès : sauvegarder avec le XML généré
                mt103Msg.setPacs008Xml(result.getXmlContent());
                logger.info("MT103 conversion succeeded for user {} - XML length: {} - saving to database",
                           currentUser, result.getXmlContent() != null ? result.getXmlContent().length() : 0);

                // Log détaillé pour déboguer
                logger.debug("XML content preview: {}",
                           result.getXmlContent() != null ? result.getXmlContent().substring(0, Math.min(100, result.getXmlContent().length())) : "NULL");

                // Sauvegarder le dernier fichier converti
                lastConvertedXml = result.getXmlContent();
                lastConvertedFilename = "MT103_" + System.currentTimeMillis() + ".xml";
            } else {
                // Pour les erreurs : sauvegarder SANS XML (pacs008Xml = null)
                mt103Msg.setPacs008Xml(null);  // Explicitement null pour indiquer l'erreur
                logger.warn("MT103 conversion failed for user {} - saving error to database: {}", currentUser, result.getErrorMessage());
            }

            // Sauvegarder dans tous les cas
            repository.save(mt103Msg);

            // Log détaillé après sauvegarde
            logger.info("Conversion saved to database with ID: {} for user: {} - XML present: {} - XML length: {}",
                       mt103Msg.getId(), currentUser,
                       mt103Msg.getPacs008Xml() != null,
                       mt103Msg.getPacs008Xml() != null ? mt103Msg.getPacs008Xml().length() : 0);

        } catch (Exception e) {
            logger.error("Erreur lors de la sauvegarde en base pour l'utilisateur {}: {}", currentUser, e.getMessage(), e);
        }

        // Retourner la réponse selon le résultat
        if (result.isSuccess()) {
            return ResponseEntity.ok(new ConversionResponse(true, result.getXmlContent(), null));
        } else {
            return ResponseEntity.badRequest()
                    .body(new ConversionResponse(false, null, result.getErrorMessage()));
        }
    }

    //historique de messages entrés
    @GetMapping("/history")
    public ResponseEntity<List<MT103Msg>> getHistory() {
        List<MT103Msg> messages = converter.getAllMessages();
        return ResponseEntity.ok(messages);
    }
    //option d'effacer l'historique
    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory() {
        repository.deleteAll();
        return ResponseEntity.noContent().build();
    }

    // Télécharger le contenu MT103 depuis l'historique
    @GetMapping("/history/{id}/download/mt103")
    public ResponseEntity<String> downloadMT103FromHistory(@PathVariable String id) {
        Optional<MT103Msg> messageOpt = repository.findById(id);
        if (messageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MT103Msg message = messageOpt.get();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=MT103_" + id + ".txt");
        headers.add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8");

        return ResponseEntity.ok()
                .headers(headers)
                .body(message.getRawContent());
    }

    // Télécharger le contenu XML depuis l'historique
    @GetMapping("/history/{id}/download/xml")
    public ResponseEntity<String> downloadXMLFromHistory(@PathVariable String id) {
        Optional<MT103Msg> messageOpt = repository.findById(id);
        if (messageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MT103Msg message = messageOpt.get();
        if (message.getPacs008Xml() == null || message.getPacs008Xml().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pacs008_" + id + ".xml");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8");

        return ResponseEntity.ok()
                .headers(headers)
                .body(message.getPacs008Xml());
    }

    // Télécharger le dernier fichier converti
    @GetMapping("/download/pacs008")
    public ResponseEntity<String> downloadLastConvertedFile() {
        if (lastConvertedXml == null) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + (lastConvertedFilename != null ? lastConvertedFilename : "pacs008.xml"));
        headers.add(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8");

        return ResponseEntity.ok()
                .headers(headers)
                .body(lastConvertedXml);
    }

    // Classe pour la réponse JSON
    public static class ConversionResponse {
        private boolean success;
        private String xmlContent;
        private String errorMessage;

        public ConversionResponse(boolean success, String xmlContent, String errorMessage) {
            this.success = success;
            this.xmlContent = xmlContent;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getXmlContent() {
            return xmlContent;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
