package com.cognivault.audit;
import java.lang.annotation.*;
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction { String action(); String entityType(); }