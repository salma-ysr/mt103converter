# SPRING SECURITY ET KEYCLOAK

## Rapport de Documentation et Spécification Fonctionnelle
#### Présenté à Mr. Anas Dbichi, Mr. Yassine El Ghazouani



#### Préparé par Salma Yousry
29 Juillet 2025, Attijariwafa Bank


#### Spring Security
Spring Security est un framework de sécurité offert par Spring, destiné à gérer l'authentification et l'autorisation 
dans les applications Java. Il permet de restreindre l'accès aux ressources de manière flexible, en fonction de conditions personnalisables.
Dans notre projet, Spring Security a été utilisé pour sécuriser les endpoints REST en fonction du rôle de l'utilisateur, 
tout en intégrant une authentification déléguée Keycloak, un fournisseur externe.


#### Keycloak
Keycloak est un serveur d'identité open-source qui gère l'authentification, 
la gestion des utilisateurs, des rôles, des groupes et des sessions. 
Il repose sur des standards comme OAuth2.0, OpenID Connect et SAML. 
Il permet de centraliser la gestion des identités en dehors de l'application métier, 
ce qui améliore la sécurité et simplifie le développement.
Dans notre application, Keycloak agit comme serveur d’autorisation. 
Il génère et valide les tokens d’accès (JWT) pour les utilisateurs authentifiés. 
Nous avons configuré un realm spécifique (Mt103-Converter) contenant un client nommé spring-app, 
ainsi que des utilisateurs avec des rôles spécifiques (USER, ADMIN).

#### JSON Web Token
Un JWT (JSON Web Token) est un jeton d'accès signé, qui encapsule un ensemble
d'informations ("claims") sur l'utilisateur (son identité, ses rôles, la durée de validité du token, etc.).
Le JWT est transmis par le client à chaque requête HTTP.
Dans notre cas, Keycloak génère le token JWT une fois que l'utilisateur est connecté. 
Spring Security le récupère et l'analyse afin de déterminer si l'utilisateur est autorisé à accéder à une ressource donnée.

#### JSON Web Key
Un JWK (JSON Web Key) est une clé publique utilisée pour vérifier la signature des tokens JWT 
émis par le serveur d’autorisation. Spring Security utilise l'URL jwk-set-uri 
(dans application.properties) pour récupérer les clés publiques de Keycloak. 
Cela permet de s'assurer que les tokens reçus sont authentiques et n'ont pas été modifiés.

#### Fonctionnement du système
L'utilisateur s'authentifie via Keycloak (via une interface web ou autre mécanisme). Une fois connecté, Keycloak émet un token JWT signé et le renvoie au client. Le client inclut ce token dans l'en-tête des requêtes HTTP. L'application, configurée comme Resource Server, intercepte chaque requête : elle vérifie la validité du token grâce aux clés JWK de Keycloak, extrait les rôles de l’utilisateur via le convertisseur JWT et autorise ou non l’accès à la ressource demandée selon les règles définies. En cas d'accès refusé, l'utilisateur reçoit une réponse 403 Forbidden.

#### Utilité dans notre application
Ce mécanisme permet de sécuriser les endpoints REST exposés par notre application, 
en garantissant que seuls les utilisateurs correctement authentifiés et autorisés peuvent 
effectuer certaines actions. Par exemple un utilisateur sans rôle ADMIN ne peut pas accéder aux routes /admin/** et un utilisateur non connecté ne peut pas accéder aux routes /user/**. En externalisant la gestion des identités et des rôles à Keycloak, on évite de gérer manuellement des utilisateurs ou des mots de passe dans notre code, ce qui réduit les risques de failles de sécurité et facilite la maintenance.


### Spécification Fonctionnelle : Configuration de la sécurité avec Spring Security et Keycloak
#### Dépendances Maven
Nous utilisons spring-boot-starter-security et spring-boot-starter-oauth2-resource-server, qui permettent à Spring de jouer le rôle de Resource Server dans le modèle OAuth2.
Les dépendances utilisées:

    <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>

#### Configuration dans application.properties
On indique l’URL du realm Keycloak et les certificats JWK pour permettre à Spring de valider les tokens JWT.

    #URL du realm Keycloak
    spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/Mt103-Converter
    #URL du jeu de clés publiques
    spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/realms/Mt103-Converter/protocol/openid-connect/certs


#### Filtrage des accès dans SecurityConfig
À l’aide de SecurityFilterChain, nous définissons des règles d'accès selon le rôle présent dans le token comme suit.

    http.authorizeHttpRequests(authorize -> authorize
    .requestMatchers("/public/**").permitAll()
    .requestMatchers("/admin/**").hasRole("ADMIN")
    .requestMatchers("/user/**").hasRole("USER")
    .anyRequest().authenticated()
    )

#### Extraction des rôles du token JWT
Keycloak encode les rôles dans le resource_access du JWT alors nous avons
implementé un convertisseur pour extraire les rôles depuis la claim resource_access.spring-app.roles
et les rendre utilisables par Spring Security.


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


#### Contrôleur de test (SecController)

Permet de tester si l’authentification fonctionne et de voir les rôles :

    @GetMapping("user/hello")
    public String userHello(Authentication auth) {
    return "this is user. rôles Spring : " + auth.getAuthorities();
    }

#### Le setup de Keycloak
Keycloak run sur http://localhost:8080.
Il faut créer un realm nommé Mt103-Converter, un client nommé spring-app (confidential, 
accès par token) et des users avec rôles USER et ADMIN associés via Client Roles/spring-app.


### BIBLIOGRAPHIE
- Keycloak
  - https://www.keycloak.org/docs/latest/release_notes/index.html#keycloak-26-3-0
  - https://www.keycloak.org/getting-started/getting-started-zip
  - https://www.youtube.com/watch?v=toEVcosbedw&ab_channel=KSTechnoWorld
- Spring Security
  - https://www.marcobehler.com/guides/spring-security
  - https://medium.com/@ihor.polataiko/spring-security-guide-part-1-introduction-c2709ff1bd98
  - https://www.youtube.com/watch?v=QKQiAB3cHXw&t=1s&ab_channel=CoffeeJug
  - https://dev.to/bansikah/keycloak-and-spring-boot-the-ultimate-guide-to-implementing-single-sign-on-1af7