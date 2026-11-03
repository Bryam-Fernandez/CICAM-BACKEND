package com.cicam.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

/** Todas las respuestas de error tienen la forma {"mensaje": "..."} para que Android la lea fácilmente. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** En desarrollo se muestra la causa del error 500 para encontrar rápido el problema. */
    @Value("${cicam.errores.detalle:true}")
    private boolean mostrarDetalle;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estado(ResponseStatusException e) {
        String msg = e.getReason() == null ? "Error" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensaje", msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", "Cuerpo de la solicitud inválido"));
    }

    /** Cualquier otro error: se registra en la consola y se responde 500 en JSON (no 401). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> inesperado(Exception e) {
        // Errores propios de Spring MVC (parámetro faltante, ruta o método no válidos...) conservan su código
        if (e instanceof org.springframework.web.ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode())
                    .body(Map.of("mensaje", er.getBody().getDetail() == null ? "Solicitud no válida" : er.getBody().getDetail()));
        }
        log.error("Error interno del servidor", e);
        String msg = "Error interno del servidor";
        if (mostrarDetalle) {
            Throwable causa = e;
            while (causa.getCause() != null) causa = causa.getCause();
            String detalle = causa.getMessage() == null ? causa.getClass().getSimpleName() : causa.getMessage();
            msg += ": " + (detalle.length() > 200 ? detalle.substring(0, 200) : detalle);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensaje", msg));
    }
}
