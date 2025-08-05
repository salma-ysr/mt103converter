package com.attijari.MT103converter.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "forward:/dashboard.html";
        } else {
            return "forward:/login.html";
        }
    }

    @GetMapping("/login")
    public String loginRedirect() {
        return "forward:/login.html";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/dashboard.html";
    }

    @GetMapping("/historique")
    public String historique() {
        return "forward:/historique.html";
    }

    @GetMapping("/conversion")
    public String conversion() {
        return "forward:/index.html";
    }
}