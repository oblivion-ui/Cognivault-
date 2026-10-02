package com.cognivault.config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Configuration
@EnableConfigurationProperties(CogniVaultProperties.class)
public class PropertiesConfig {}