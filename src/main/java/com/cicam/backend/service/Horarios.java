package com.cicam.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Horarios de medicamentos: horas de 24 h separadas por comas, p. ej. «08:00, 20:00». */
public final class Horarios {

    private static final Pattern HORA = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private Horarios() {
    }

    /** Devuelve las horas válidas del texto (las inválidas se ignoran). */
    public static List<LocalTime> parsear(String texto) {
        List<LocalTime> horas = new ArrayList<>();
        if (texto == null) return horas;
        for (String parte : texto.split(",")) {
            String t = parte.trim();
            if (HORA.matcher(t).matches()) horas.add(LocalTime.parse(t));
        }
        return horas;
    }

    /** Valida el formato y lo deja normalizado («08:00, 20:00»). */
    public static String normalizar(String texto) {
        List<String> partes = new ArrayList<>();
        for (String parte : texto.split(",")) {
            String t = parte.trim();
            if (!HORA.matcher(t).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Los horarios deben tener el formato 08:00, 20:00 (hora de 24 horas separada por comas)");
            }
            partes.add(t);
        }
        return String.join(", ", partes);
    }
}
