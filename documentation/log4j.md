# GESTION DES LOGS AVEC LOG4J

## Rapport de Documentation et Spécification Fonctionnelle

### Présenté à Mr. Anas Dbichi, Mr. Yassine El Ghazouani

### Préparé par Ryad Fettane, Salma Yousry


30 Juillet 2025, Attijariwafa Bank

#### Qu’est-ce que log4j2 ?

Log4j2 est une bibliothèque Java de logging permettant d’enregistrer des messages (logs) dans la console, des fichiers, ou d’autres destinations. Elle est très utilisée pour le suivi, le diagnostic et le débogage des applications.

#### Principales fonctionnalités
- Niveaux de log : TRACE, DEBUG, INFO, WARN, ERROR, FATAL
- Configuration flexible : XML, JSON, YAML, ou propriétés
- Appenders : Console, File, RollingFile, etc.
- Formatage des messages : Personnalisation du format via PatternLayout
- Performance : Asynchrone, filtrage, gestion fine des logs

#### Intégration de Log4J dans notre projet
Nous avons intégré Log4j version 2 via les modules suivants dans pom.xml :

    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-api</artifactId>
        <version>2.20.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-core</artifactId>
        <version>2.20.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-slf4j-impl</artifactId>
        <version>2.20.0</version>
    </dependency>

Où 'log4j-slf4j-impl' permet de faire le lien entre Log4j et le système de logging unifié (SLF4J) utilisé par Spring Boot.

#### Configuration de Log4j dans notre projet

La configuration de Log4j2 est centralisée dans un fichier XML situé dans src/main/resources/log4j2.xml. Ce fichier déclare
•	un appender de type Console, qui affiche les logs dans le terminal
•	un logger racine (Root Logger) au niveau info, qui capte tous les logs
•	un logger spécifique à notre package com.attijari.MT103converter, avec un niveau debug pour afficher plus d’informations lors du développement.

log4j.xml:
    
    <Configuration status="WARN">
    <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
    <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n"/>
    </Console>
    </Appenders>
    <Loggers>
    <Root level="info">
    <AppenderRef ref="Console"/>
    </Root>
    <Logger name="com.attijari.MT103converter" level="debug" additivity="false">
    <AppenderRef ref="Console"/>
    </Logger>
    </Loggers>
    </Configuration>

La sortie affichée suit un format lisible incluant la date, le niveau du log, la classe et ligne concernée, puis le message.

#### Utilisation dans notre application
Log4j est utilisé via une instance Logger, en général définie comme attribut statique d’une classe, par exemple :

    private static final Logger logger = LogManager.getLogger(Transformer.class);





#### Exemple de logging dans une méthode de transformation

    public Pacs008Msg transform(MT103Msg mt103) {
    logger.debug("Transforming MT103Msg to Pacs008Msg");
    if (mt103 == null) {
    logger.error("MT103Msg is null, cannot transform");
    return null;
    }
    String xml = generatePacs008Xml(mt103);
    logger.info("Transformation complete. XML length: {}", xml.length());
    return new Pacs008Msg(xml);
    }

Où :
- debug est utilisé pour tracer l’entrée dans un processus technique.
- error signale une anomalie grave (entrée invalide).
- info indique une étape réussie ou une donnée utile (ici, la taille du fichier généré).
dans certaines méthodes plus bas niveau, on utilise aussi trace, qui est encore plus détaillé.







_**Documentation officielle**_
- Site officiel log4j2 : https://logging.apache.org/log4j/2.x/manual/index.html
- Configuration XML : https://logging.apache.org/log4j/2.x/manual/configuration.html
- API Java : https://logging.apache.org/log4j/2.x/log4j-api/apidocs/index.html

