package com.attijari.MT103converter.services;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import java.util.*;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        try {
            http
                .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(
                        "/login","/login.html","/css/**","/js/**","/images/**","/static/**","/*.png","/*.css","/*.js","/error"
                    ).permitAll()
                    .requestMatchers("/public/**").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers("/user/**").hasRole("USER")
                    .requestMatchers(
                        "/","/index.html","/dashboard","/historique","/conversion","/conversion-inverse",
                        "/api/**","/convert","/convert-reverse","/download/**"
                    ).authenticated()
                    .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                    .ignoringRequestMatchers(
                        "/api/**","/convert","/convert-reverse","/download/**"
                    )
                )
                .oauth2Login(oauth2 -> oauth2
                    .loginPage("/login.html")
                    .defaultSuccessUrl("/dashboard", true)
                    .failureUrl("/login.html?error=true")
                )
                .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login.html?logout=true")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
                    .permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .sessionManagement(session -> session
                    .invalidSessionUrl("/login.html?expired=true")
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
                );
        } catch (Exception e) {
            System.err.println("Erreur de chargement de Keycloak. Mode développement activé.");
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(csrf -> csrf.disable());
        }
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null && resourceAccess.containsKey("spring-app")) {
                Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get("spring-app");
                List<String> roles = (List<String>) clientAccess.get("roles");
                if (roles != null) {
                    for (String role : roles) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }
}
