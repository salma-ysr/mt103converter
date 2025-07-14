package com.attijari.MT103converter.services;

import com.attijari.MT103converter.models.MT103Msg;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 test pour la classe MT103Parser
 Vérifie que les champs sont extraits du message brut
 */
public class MT103ParserTest {

    @Test
    public void testParseSimpleMT103() {
        // message MT103 brut
        String rawMessage = """
            :20:REF12345
            :32A:240714EUR10000,
            :50K:/12345678
            ALICE SMITH
            :59:/87654321
            BOB SMITH
            """;

        // instanciation du parser
        MT103Parser parser = new MT103Parser();
        MT103Msg parsed = parser.parse(rawMessage);

        // vérifie que le contenu brut a été enregistré
        assertEquals(rawMessage, parsed.getRawContent());

        // get la map des champs
        Map<String, String> fields = parsed.getFields();

        // vérifie des champs
        assertEquals("REF12345", fields.get("20"));
        assertEquals("240714EUR10000,", fields.get("32A"));

        // vérifie un champ de plusieurs lignes
        assertEquals("/12345678\nALICE SMITH", fields.get("50K"));
        assertEquals("/87654321\nBOB SMITH", fields.get("59"));

        // isValid = true si les champs requis sont là
        assertTrue(parsed.isValid());
    }
}
