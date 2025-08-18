package com.attijari.MT103converter.models;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * Stocke une conversion PACS008 -> MT103
 */
@Document(collection = "PACS008_CONVERSIONS")
public class Pacs008ToMT103Conversion {
    @Id
    private String id;
    private String rawPacs008Xml; // input
    private String mt103Result;   // output (peut être null si erreur)
    private String errorMessage;  // message d'erreur éventuel
    private boolean success;
    private String username; // utilisateur

    @CreatedDate
    private LocalDateTime createdAt = LocalDateTime.now();

    public Pacs008ToMT103Conversion() {}

    public Pacs008ToMT103Conversion(String rawPacs008Xml) {
        this.rawPacs008Xml = rawPacs008Xml;
    }

    public String getId() { return id; }
    public String getRawPacs008Xml() { return rawPacs008Xml; }
    public void setRawPacs008Xml(String rawPacs008Xml) { this.rawPacs008Xml = rawPacs008Xml; }
    public String getMt103Result() { return mt103Result; }
    public void setMt103Result(String mt103Result) { this.mt103Result = mt103Result; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

