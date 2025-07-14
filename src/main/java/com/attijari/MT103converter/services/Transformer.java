package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.Pacs008Msg;
import org.springframework.stereotype.Service;

/**
 * transformer un message MT103 en message pacs.008
 */
@Service
public class Transformer {

    /**
     * Transforme MT103Msg en Pacs008Msg.
     *
     * @param mt103 message brut/initial
     * @return message transformé
     */
    public Pacs008Msg transform(MT103Msg mt103) {
        // Todo
        return null;
    }
}
