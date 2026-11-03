package com.cicam.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Limita los intentos fallidos de ingreso con código por dirección IP (evita adivinar códigos). */
@Component
public class LimitadorCodigo {

    private static final int MAX_FALLOS = 5;
    private static final Duration VENTANA = Duration.ofMinutes(15);

    private static class Registro {
        int fallos;
        LocalDateTime inicio;
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    public void verificar(String ip) {
        Registro r = registros.get(ip);
        if (r == null) return;
        if (r.inicio.plus(VENTANA).isBefore(LocalDateTime.now())) {
            registros.remove(ip);
        } else if (r.fallos >= MAX_FALLOS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo.");
        }
    }

    public void fallo(String ip) {
        registros.compute(ip, (k, r) -> {
            if (r == null || r.inicio.plus(VENTANA).isBefore(LocalDateTime.now())) {
                r = new Registro();
                r.inicio = LocalDateTime.now();
            }
            r.fallos++;
            return r;
        });
    }

    public void exito(String ip) {
        registros.remove(ip);
    }
}
