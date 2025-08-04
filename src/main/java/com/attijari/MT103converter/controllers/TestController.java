package com.attijari.MT103converter.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/index.html";
        } else {
            return "redirect:/login.html";
        }
    }

    @GetMapping("/login")
    public String loginRedirect() {
        return "redirect:/login.html";
    }
}