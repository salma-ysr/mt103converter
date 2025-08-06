package com.attijari.MT103converter.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Contrôleur pour servir les pages HTML statiques
 */
@Controller
public class PageController {

    /**
     * Page d'accueil - redirige vers dashboard.html
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    /**
     * Page dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard.html";
    }

    /**
     * Page de conversion
     */
    @GetMapping("/conversion")
    public String conversion() {
        return "index.html";
    }

    /**
     * Page historique
     */
    @GetMapping("/historique")
    public String historique() {
        return "historique.html";
    }

    /**
     * Page de login
     */
    @GetMapping("/login")
    public String login() {
        return "login.html";
    }
}
