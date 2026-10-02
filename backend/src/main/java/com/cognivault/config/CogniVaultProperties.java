package com.cognivault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "cognivault")
public record CogniVaultProperties(
        Jwt jwt,
        Bootstrap bootstrap,
        Ml ml,
        Websocket websocket,
        AnomalyRisk anomalyRisk) {

    @ConstructorBinding
    public CogniVaultProperties(
            Jwt jwt,
            Bootstrap bootstrap,
            Ml ml,
            Websocket websocket,
            AnomalyRisk anomalyRisk) {
        this.jwt = jwt;
        this.bootstrap = bootstrap;
        this.ml = ml;
        this.websocket = websocket;
        this.anomalyRisk = anomalyRisk;
    }

    public CogniVaultProperties(
            Jwt jwt,
            Bootstrap bootstrap,
            Ml ml,
            Websocket websocket) {
        this(jwt, bootstrap, ml, websocket, new AnomalyRisk(0.50, 0.65, 0.80));
    }

    public record Jwt(String secret, long expirationMinutes) {}

    public record Bootstrap(
            boolean enabled,
            String username,
            String email,
            String password,
            String name,
            boolean secondAdminEnabled,
            String secondAdminUsername,
            String secondAdminEmail,
            String secondAdminPassword,
            String secondAdminName,
            boolean hrEnabled,
            boolean hrResetPassword,
            String hrUsername,
            String hrEmail,
            String hrPassword,
            String hrName) {
    }

    public record Ml(String baseUrl, String apiKey) {}

    public record Websocket(String allowedOrigins) {}

    public record AnomalyRisk(
            double mediumThreshold,
            double highThreshold,
            double criticalThreshold) {}
}
