package com.cicam.backend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Textos {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private Textos() {
    }

    /** 12/10 08:30 */
    public static String fecha(LocalDateTime d) {
        return d == null ? "-" : d.format(FECHA_HORA);
    }
}
