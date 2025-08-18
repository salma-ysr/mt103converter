package com.attijari.MT103converter.converters;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests basiques pour la conversion inverse PACS008 -> MT103 */
public class Pacs008ToMT103ConverterTest {

    @Test
    void testReverseConversionHappyPath() {
        String pacs = """
            <?xml version=\"1.0\" encoding=\"UTF-8\"?>
            <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">
              <FIToFICstmrCdtTrf>
                <GrpHdr>
                  <MsgId>MSG123</MsgId>
                  <CreDtTm>2025-07-14T10:15:30</CreDtTm>
                  <NbOfTxs>1</NbOfTxs>
                </GrpHdr>
                <CdtTrfTxInf>
                  <PmtId>
                    <InstrId>INSTR-1</InstrId>
                    <EndToEndId>E2E-1</EndToEndId>
                  </PmtId>
                  <PmtTpInf><SvcLvl><Cd>NORM</Cd></SvcLvl></PmtTpInf>
                  <IntrBkSttlmAmt Ccy=\"EUR\">1234.56</IntrBkSttlmAmt>
                  <IntrBkSttlmDt>2025-07-14</IntrBkSttlmDt>
                  <ChrgBr>SHAR</ChrgBr>
                  <Dbtr>
                    <Nm>ALPHA COMPANY</Nm>
                    <PstlAdr><AdrLine>1 RUE A</AdrLine><AdrLine>75000 PARIS</AdrLine></PstlAdr>
                  </Dbtr>
                  <DbtrAcct><Id><Othr><Id>DEBTACC1</Id></Othr></Id></DbtrAcct>
                  <Cdtr>
                    <Nm>BETA SARL</Nm>
                    <PstlAdr><AdrLine>2 AV B</AdrLine></PstlAdr>
                  </Cdtr>
                  <CdtrAcct><Id><Othr><Id>CREDACC9</Id></Othr></Id></CdtrAcct>
                  <RmtInf><Ustrd>Facture 2025-07</Ustrd></RmtInf>
                </CdtTrfTxInf>
              </FIToFICstmrCdtTrf>
            </Document>
            """;
        Pacs008ToMT103Converter converter = new Pacs008ToMT103Converter();
        Pacs008ToMT103Converter.ConversionResult res = converter.process(pacs);
        assertTrue(res.isSuccess(), () -> "Conversion devrait réussir: " + res.getErrorMessage());
        String mt = res.getMt103Content();
        assertNotNull(mt);
        assertTrue(mt.contains(":20:INSTR-1"));
        assertTrue(mt.contains(":32A:250714EUR1234,56"), mt);
        assertTrue(mt.contains("/DEBTACC1"));
        assertTrue(mt.contains("/CREDACC9"));
        assertTrue(mt.contains(":70:Facture 2025-07"));
    }

    @Test
    void testReverseConversionMissingTx() {
        String pacs = """
            <?xml version=\"1.0\"?>
            <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">
              <FIToFICstmrCdtTrf>
                <GrpHdr><MsgId>MSG123</MsgId></GrpHdr>
              </FIToFICstmrCdtTrf>
            </Document>
            """;
        Pacs008ToMT103Converter converter = new Pacs008ToMT103Converter();
        Pacs008ToMT103Converter.ConversionResult res = converter.process(pacs);
        assertFalse(res.isSuccess());
        assertTrue(res.getErrorMessage().contains("CdtTrfTxInf"));
    }
}

