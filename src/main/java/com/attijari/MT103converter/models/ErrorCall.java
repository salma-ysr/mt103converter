package com.attijari.MT103converter.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Représenter erreurs de validation
 */
public class ErrorCall {

    private List<String> errors = new ArrayList<>();

    public void addError(String error) {
        errors.add(error);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }
}
