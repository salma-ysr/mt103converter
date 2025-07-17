package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.converters.MT103ToPacs008Converter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MT103Controller {

    private final MT103ToPacs008Converter converter;

    public MT103Controller(MT103ToPacs008Converter converter) {
        this.converter = converter;
    }

    @PostMapping(value = "/convert", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> convertMt103(@RequestBody String rawMt103) {
        String result = converter.process(rawMt103);

        // détecter erreur XML & donner code HTTP400
        if (result.contains("<error>")) {
            return ResponseEntity.badRequest().body(result);
        }

        return ResponseEntity.ok(result);
    }
}
