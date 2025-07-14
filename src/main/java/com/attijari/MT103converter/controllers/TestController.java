package com.attijari.MT103converter.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/")
    public String testSuccessLoad() {
        return "Successfully loaded Spring";
    }
}