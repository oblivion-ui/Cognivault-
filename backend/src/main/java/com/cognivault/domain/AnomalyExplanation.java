package com.cognivault.domain;

public record AnomalyExplanation(
        String feature,
        double contribution,
        String direction,
        String explanation) {
}
