package com.cognivault;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
@SpringBootApplication
@EnableAsync
public class CogniVaultApplication { public static void main(String[] args){SpringApplication.run(CogniVaultApplication.class,args);} }