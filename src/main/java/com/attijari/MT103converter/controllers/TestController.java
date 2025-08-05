package com.attijari.MT103converter.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
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

    @GetMapping("/hello")
    public String helloUser(Authentication authentication) {
        return "Bonjour " + authentication.getName() + " ! rôle(s) : " + authentication.getAuthorities();
    }

    @GetMapping("/login")
    public String loginRedirect() {
        return "redirect:/login.html";
    }
}