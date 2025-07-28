package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.converters.MT103ToPacs008Converter;
import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.repositories.MT103MsgRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class MT103Controller {
    private static final Logger logger = LogManager.getLogger(MT103Controller.class);

    private final MT103ToPacs008Converter converter;
    private final MT103MsgRepository repository;


    public MT103Controller(MT103ToPacs008Converter converter, MT103MsgRepository repository) {
        this.converter = converter;
        this.repository = repository;
    }


    @PostMapping(value = "/convert", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConversionResponse> convertMt103(@RequestBody String rawMt103) {
        logger.info("Received MT103 file for conversion");
        MT103ToPacs008Converter.ConversionResult result = converter.process(rawMt103);

        if (result.isSuccess()) {
            logger.info("MT103 conversion succeeded");
            return ResponseEntity.ok(new ConversionResponse(true, result.getXmlContent(), null));
        } else {
            logger.error("MT103 conversion failed: {}", result.getErrorMessage());
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
