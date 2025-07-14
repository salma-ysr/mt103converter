package com.attijari.MT103converter.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Map;

@Document(collection = "MT103")
public class MT103Msg {

    @Id
    private String id;

    private String rawContent;
    private Map<String, String> fields;

    public MT103Msg() {
        //bare constructor
    }

    public MT103Msg(String rawContent, Map<String, String> fields) {
        this.rawContent = rawContent;
        this.fields = fields;
    }

    public String getField(String tag) {
        /*
        extract and return fields from MT103 message
         */
        String orDefault = fields.getOrDefault(tag, "");
        return orDefault;
    }

    public boolean isValid() {
        /*
        checks whether the parsed MT103 message has valid structure & contains all required fields
        TODO : add other requirement checks
         */
        // simplified example
        boolean validFlag = fields != null && fields.containsKey("20") && fields.containsKey("32A");
        return validFlag;
    }

    // getters and setters (use Lombok?)
    public String getRawContent() {
        return rawContent;
    }
    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
