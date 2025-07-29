package com.attijari.MT103converter.services;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/")
public class SecController {
    //to test auth.

    @GetMapping("user/hello")
    public String userHello(Authentication auth) {
        return "this is user. rôles Spring : " + auth.getAuthorities();
    }

    @GetMapping("public/hello")
    public String publicHello() {
        return "this is public";
    }

    @GetMapping("admin/hello")
    public String adminHello() {
        return "this is admin";
    }
}
