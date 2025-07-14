package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.Pacs008Msg;
import org.springframework.stereotype.Service;

/**
 * Gérer parsing, validation, transformation
 */
@Service
public class Converter {

    /**
     * Conversion MT103 ➝ pacs.008
     *
     * @param rawMT103 texte brut
     * @return message pacs.008 final
     */
    public Pacs008Msg process(String rawMT103) {
        // Todo
        return null;
    }
}
