package com.cognivault.api;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@RestControllerAdvice
public class GlobalExceptionHandler {
 private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR",e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).collect(Collectors.joining("; ")),Instant.now()));}
 @ExceptionHandler(BadCredentialsException.class) ResponseEntity<ApiError> credentials(){return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("INVALID_CREDENTIALS","Invalid credentials"));}
 @ExceptionHandler(AuthenticationException.class) ResponseEntity<ApiError> authentication(AuthenticationException e){return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("UNAUTHORIZED","Authentication failed"));}
 @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ApiError> forbidden(AccessDeniedException e){return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of("FORBIDDEN","You do not have permission to perform this action."));}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ApiError> illegal(IllegalArgumentException e){return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST",e.getMessage()));}
 @ExceptionHandler(RuntimeException.class) ResponseEntity<ApiError> runtime(RuntimeException e){log.error("Unhandled backend exception",e);return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of("INTERNAL_ERROR","The backend could not complete this request."));}
}


\n