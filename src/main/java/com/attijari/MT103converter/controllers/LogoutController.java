package com.attijari.MT103converter.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class LogoutController {

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:http://localhost:8080/realms/Mt103-Converter}")
    private String keycloakIssuerUri;

    @GetMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        // 1. Invalider complètement la session
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // 2. Nettoyer le contexte de sécurité
        SecurityContextHolder.clearContext();

        // 3. Utiliser le SecurityContextLogoutHandler
        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.setInvalidateHttpSession(true);
        logoutHandler.setClearAuthentication(true);
        logoutHandler.logout(request, response, authentication);

        // 4. URL de redirection après déconnexion Keycloak (sans paramètres)
        String redirectUri = "http://localhost:8081/login.html";
        String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        // 5. Si l'utilisateur est connecté via OIDC, déconnexion complète Keycloak
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
            String idToken = oidcUser.getIdToken().getTokenValue();

            // URL de déconnexion Keycloak avec id_token_hint
            String logoutUrl = keycloakIssuerUri + "/protocol/openid-connect/logout" +
                    "?id_token_hint=" + idToken +
                    "&post_logout_redirect_uri=" + encodedRedirectUri;

            response.sendRedirect(logoutUrl);
        } else {
            // Déconnexion simple - redirection directe
            response.sendRedirect("/login.html");
        }
    }
}
