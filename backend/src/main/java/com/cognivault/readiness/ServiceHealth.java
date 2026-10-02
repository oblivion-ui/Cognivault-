package com.cognivault.readiness;
import java.time.Instant;
public record ServiceHealth(String service,ReadinessStatus status,Instant checkedAt,Long latencyMs,String details){}