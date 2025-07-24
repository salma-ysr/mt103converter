package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.converters.MT103ToPacs008Converter;
import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.repositories.MT103MsgRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class MT103Controller {

    private final MT103ToPacs008Converter converter;
    private final MT103MsgRepository repository;


    public MT103Controller(MT103ToPacs008Converter converter, MT103MsgRepository repository) {
        this.converter = converter;
        this.repository = repository;
    }


    @PostMapping(value = "/convert", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConversionResponse> convertMt103(@RequestBody String rawMt103) {
        MT103ToPacs008Converter.ConversionResult result = converter.process(rawMt103);

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
