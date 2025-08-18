package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.converters.Pacs008ToMT103Converter;
import com.attijari.MT103converter.models.Pacs008ToMT103Conversion;
import com.attijari.MT103converter.repositories.Pacs008ToMT103ConversionRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

@RestController
public class Pacs008ToMT103Controller {

    private static final Logger logger = LogManager.getLogger(Pacs008ToMT103Controller.class);
    private final Pacs008ToMT103Converter converter;
    private final Pacs008ToMT103ConversionRepository repository;

    public Pacs008ToMT103Controller(Pacs008ToMT103Converter converter, Pacs008ToMT103ConversionRepository repository) {
        this.converter = converter;
        this.repository = repository;
    }

    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
                    return oidcUser.getPreferredUsername();
                }
                return authentication.getName();
            }
        } catch (Exception e) {
            logger.warn("Impossible de récupérer l'utilisateur connecté: {}", e.getMessage());
        }
        return "anonymous";
    }

    @PostMapping(value = "/convert-reverse", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_PLAIN_VALUE}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReverseResponse> convertReverse(@RequestBody String pacsXml) {
        String user = getCurrentUsername();
        logger.info("Reverse conversion request by {} length={} chars", user, pacsXml != null ? pacsXml.length() : 0);

        Pacs008ToMT103Converter.ConversionResult result = converter.process(pacsXml);

        // Persist
        Pacs008ToMT103Conversion entity = new Pacs008ToMT103Conversion();
        entity.setRawPacs008Xml(pacsXml);
        entity.setSuccess(result.isSuccess());
        entity.setMt103Result(result.getMt103Content());
        entity.setErrorMessage(result.getErrorMessage());
        entity.setUsername(user);
        repository.save(entity);

        if (result.isSuccess()) {
            return ResponseEntity.ok(new ReverseResponse(true, result.getMt103Content(), null));
        } else {
            return ResponseEntity.badRequest().body(new ReverseResponse(false, null, result.getErrorMessage()));
        }
    }

    public record ReverseResponse(boolean success, String mt103Content, String errorMessage) {}
}

