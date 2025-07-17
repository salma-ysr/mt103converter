package com.attijari.MT103converter.converters;

import org.springframework.stereotype.Component;

@Component
public class MT103ToPacs008Converter {

    public String process(String rawMT103) {
        // fake traitement pour tester pipeline controller-converter
        if (rawMT103 == null || rawMT103.isEmpty()) {
            return "<error>Empty MT103 message</error>";
        }

        // simulateur d’un XML généré
        String fakeXml =
                "<Document>\n" +
                        "  <CstmrCdtTrfInitn>\n" +
                        "    <MsgId>FAKE123456</MsgId>\n" +
                        "    <PmtInf>Conversion réussie </PmtInf>\n" +
                        "  </CstmrCdtTrfInitn>\n" +
                        "</Document>";

        return fakeXml;
    }
}
