package com.attijari.MT103converter.controllers;

import com.attijari.MT103converter.repositories.MT103MsgRepository;
import com.attijari.MT103converter.repositories.Pacs008ToMT103ConversionRepository;
import com.attijari.MT103converter.models.MT103Msg;
import com.attijari.MT103converter.models.Pacs008ToMT103Conversion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API Controller pour les données de l'historique par utilisateur (deux sens)
 */
@RestController
public class HistoriqueController {

    private static final Logger logger = LogManager.getLogger(HistoriqueController.class);

    @Autowired(required = false)
    private MT103MsgRepository mt103Repository;

    @Autowired(required = false)
    private Pacs008ToMT103ConversionRepository reverseRepository;

    /**
     * API pour récupérer l'historique des conversions de l'utilisateur connecté (2 sens)
     */
    @GetMapping("/api/historique/list")
    public Map<String, Object> getHistoriqueList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String filter) {

        String currentUser = getCurrentUsername();
        logger.debug("Récupération de l'historique (2 sens) pour {} page:{} size:{} filter:{}", currentUser, page, size, filter);
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> all = new ArrayList<>();
            // Sens MT103 -> PACS008
            if (mt103Repository != null) {
                for (MT103Msg msg : mt103Repository.findTop50ByUsernameOrderByCreatedAtDesc(currentUser)) {
                    Map<String, Object> item = buildMt103ToPacsItem(msg);
                    if (passesFilter(item, filter)) all.add(item);
                }
            }
            // Sens PACS008 -> MT103
            if (reverseRepository != null) {
                for (Pacs008ToMT103Conversion conv : reverseRepository.findTop50ByUsernameOrderByCreatedAtDesc(currentUser)) {
                    Map<String, Object> item = buildPacsToMt103Item(conv);
                    if (passesFilter(item, filter)) all.add(item);
                }
            }
            // Tri par date desc
            all.sort((a,b) -> ((LocalDateTime)a.get("dateObj")).compareTo((LocalDateTime)b.get("dateObj")) * -1);

            long totalElements = all.size();
            int totalPages = (int)Math.ceil((double)totalElements / size);
            int start = page * size;
            int end = Math.min(start + size, all.size());
            List<Map<String, Object>> pageContent = start < all.size() ? all.subList(start, end) : Collections.emptyList();
            // Nettoyer clé interne dateObj
            for (Map<String,Object> m : pageContent) { m.remove("dateObj"); }

            response.put("conversions", pageContent);
            response.put("totalPages", totalPages);
            response.put("currentPage", page);
            response.put("totalElements", totalElements);
            response.put("currentUser", currentUser);
        } catch (Exception e) {
            logger.error("Erreur historique mixte: {}", e.getMessage());
            response.put("conversions", new ArrayList<>());
            response.put("totalPages", 0);
            response.put("currentPage", 0);
            response.put("totalElements", 0);
            response.put("error", "Erreur lors du chargement de l'historique");
        }
        return response;
    }

    private boolean passesFilter(Map<String,Object> item, String filter) {
        if (filter == null || filter.isBlank()) return true;
        String f = filter.toLowerCase();
        return (item.getOrDefault("transactionId","" ).toString().toLowerCase().contains(f))
                || (item.getOrDefault("sender","" ).toString().toLowerCase().contains(f))
                || (item.getOrDefault("beneficiary","" ).toString().toLowerCase().contains(f))
                || (item.getOrDefault("type","" ).toString().toLowerCase().contains(f));
    }

    private Map<String,Object> buildMt103ToPacsItem(MT103Msg msg) {
        Map<String,Object> item = new HashMap<>();
        item.put("id", msg.getId());
        item.put("type", "MT103→PACS008");
        item.put("transactionId", safeGetField(msg, "20"));
        item.put("amount", extractAmount(safeGetField(msg, "32A")));
        item.put("currency", extractCurrency(safeGetField(msg, "32A")));
        item.put("sender", extractFirstLine(safeGetField(msg, "50K")));
        item.put("beneficiary", extractFirstLine(safeGetField(msg, "59")));
        boolean isSuccess = msg.getPacs008Xml() != null && msg.getPacs008Xml().trim().length() > 50;
        item.put("status", isSuccess ? "Succès" : "Erreur");
        item.put("date", msg.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        item.put("dateObj", msg.getCreatedAt());
        item.put("username", msg.getUsername());
        item.put("fileNameIn", "MT103_" + shortId(msg.getId()) + ".txt");
        item.put("fileNameOut", isSuccess ? "PACS008_" + shortId(msg.getId()) + ".xml" : null);
        item.put("direction", "forward");
        return item;
    }

    private Map<String,Object> buildPacsToMt103Item(Pacs008ToMT103Conversion conv) {
        Map<String,Object> item = new HashMap<>();
        item.put("id", conv.getId());
        item.put("type", "PACS008→MT103");
        // Extraire montant/devise depuis XML
        String[] amt = extractAmountCurrencyFromPacs(conv.getRawPacs008Xml());
        item.put("amount", amt[1]);
        item.put("currency", amt[0]);
        item.put("sender", "-");
        item.put("beneficiary", "-");
        item.put("status", conv.isSuccess() ? "Succès" : "Erreur");
        item.put("date", conv.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        item.put("dateObj", conv.getCreatedAt());
        item.put("username", conv.getUsername());
        item.put("fileNameIn", "PACS008_" + shortId(conv.getId()) + ".xml");
        item.put("fileNameOut", conv.isSuccess() ? "MT103_" + shortId(conv.getId()) + ".txt" : null);
        item.put("direction", "reverse");
        return item;
    }

    private String[] extractAmountCurrencyFromPacs(String xml) {
        if (xml == null) return new String[]{"",""};
        try {
            Pattern p = Pattern.compile("<IntrBkSttlmAmt\\s+Ccy=\"([A-Z]{3})\">([0-9.]+)</IntrBkSttlmAmt>");
            Matcher m = p.matcher(xml);
            if (m.find()) {
                return new String[]{m.group(1), m.group(2)};
            }
        } catch (Exception ignored) {}
        return new String[]{"",""};
    }

    private String shortId(String id) { return id != null ? id.substring(0, Math.min(8,id.length())) : "NA"; }

    /**
     * Téléchargement fichier (cherche dans les deux collections)
     */
    @GetMapping("/api/historique/download")
    public ResponseEntity<byte[]> download(@RequestParam String id, @RequestParam String type) {
        // Chercher d'abord sens forward
        if (mt103Repository != null && mt103Repository.findById(id).isPresent()) {
            MT103Msg msg = mt103Repository.findById(id).get();
            String filename;
            String content;
            if ("mt103".equalsIgnoreCase(type)) {
                filename = "MT103_" + shortId(id) + ".txt";
                content = msg.getRawContent();
            } else if ("pacs008".equalsIgnoreCase(type)) {
                if (msg.getPacs008Xml() == null) return ResponseEntity.badRequest().build();
                filename = "PACS008_" + shortId(id) + ".xml";
                content = msg.getPacs008Xml();
            } else return ResponseEntity.badRequest().build();
            return buildDownload(content, filename);
        }
        // Sens reverse
        if (reverseRepository != null && reverseRepository.findById(id).isPresent()) {
            Pacs008ToMT103Conversion conv = reverseRepository.findById(id).get();
            String filename;
            String content;
            if ("pacs008".equalsIgnoreCase(type)) {
                filename = "PACS008_" + shortId(id) + ".xml";
                content = conv.getRawPacs008Xml();
            } else if ("mt103".equalsIgnoreCase(type)) {
                if (!conv.isSuccess() || conv.getMt103Result() == null) return ResponseEntity.badRequest().build();
                filename = "MT103_" + shortId(id) + ".txt";
                content = conv.getMt103Result();
            } else return ResponseEntity.badRequest().build();
            return buildDownload(content, filename);
        }
        return ResponseEntity.notFound().build();
    }

    private ResponseEntity<byte[]> buildDownload(String content, String filename) {
        if (content == null) content = "";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    // === Méthodes existantes adaptées ===

    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                if (authentication.getPrincipal() instanceof OidcUser) {
                    OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
                    return oidcUser.getPreferredUsername();
                } else {
                    return authentication.getName();
                }
            }
        } catch (Exception e) {
            logger.warn("Impossible de récupérer l'utilisateur connecté: {}", e.getMessage());
        }
        return "anonymous";
    }

    @DeleteMapping("/api/historique/delete")
    public Map<String,Object> deleteHistoryItem(@RequestParam String id) {
        String currentUser = getCurrentUsername();
        Map<String,Object> resp = new HashMap<>();
        boolean deleted = false;
        if (mt103Repository != null && mt103Repository.findById(id).isPresent()) {
            MT103Msg msg = mt103Repository.findById(id).get();
            if (currentUser.equals(msg.getUsername())) { mt103Repository.deleteById(id); deleted = true; }
        }
        if (!deleted && reverseRepository != null && reverseRepository.findById(id).isPresent()) {
            Pacs008ToMT103Conversion conv = reverseRepository.findById(id).get();
            if (currentUser.equals(conv.getUsername())) { reverseRepository.deleteById(id); deleted = true; }
        }
        resp.put("success", deleted);
        resp.put("message", deleted ? "Élément supprimé" : "Introuvable ou non autorisé");
        return resp;
    }

    @DeleteMapping("/api/historique/clear")
    public Map<String,Object> clearHistory() {
        String user = getCurrentUsername();
        int count = 0;
        if (mt103Repository != null) {
            for (MT103Msg m : mt103Repository.findTop50ByUsernameOrderByCreatedAtDesc(user)) { mt103Repository.delete(m); count++; }
        }
        if (reverseRepository != null) {
            for (Pacs008ToMT103Conversion c : reverseRepository.findTop50ByUsernameOrderByCreatedAtDesc(user)) { reverseRepository.delete(c); count++; }
        }
        return Map.of("success", true, "deletedCount", count);
    }

    // Utilitaires existants (copiés)
    private String safeGetField(MT103Msg msg, String fieldTag) {
        try { String value = msg.getField(fieldTag); return value != null ? value : ""; } catch (Exception e) { return ""; }
    }
    private String extractAmount(String field32A) {
        if (field32A == null || field32A.trim().isEmpty()) { return "N/A"; }
        try { String cleanField = field32A.replaceAll("[\\r\\n]", ""); if (cleanField.length() > 9) { String amountPart = cleanField.substring(9); return amountPart.replaceAll("[^0-9.,]", ""); } } catch (Exception ignored) {}
        return "N/A";
    }
    private String extractCurrency(String field32A) {
        if (field32A == null || field32A.trim().isEmpty()) { return "N/A"; }
        try { String cleanField = field32A.replaceAll("[\\r\\n]", ""); if (cleanField.length() > 8) { return cleanField.substring(6, 9); } } catch (Exception ignored) {}
        return "N/A";
    }
    private String extractFirstLine(String field) { if (field == null || field.trim().isEmpty()) return "N/A"; try { String[] lines = field.split("\\r?\\n"); return lines.length > 0 ? lines[0].trim() : "N/A"; } catch (Exception e) { return "N/A"; } }
}
