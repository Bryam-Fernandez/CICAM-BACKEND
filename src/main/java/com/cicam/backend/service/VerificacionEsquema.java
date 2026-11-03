package com.cicam.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Al iniciar, comprueba que la base de datos tenga todas las tablas y columnas que necesita esta versión.
 * Si falta algo, el servidor no arranca y dice exactamente qué script SQL hay que ejecutar
 * (así no aparecen errores confusos más tarde).
 */
@Component
@RequiredArgsConstructor
public class VerificacionEsquema implements ApplicationRunner {

    private static final List<String> TABLAS = List.of("usuarios", "grupos", "grupo_integrantes", "cuidador_permisos",
            "codigos_acceso", "sesiones", "actividades", "historial_actividad", "citas", "medicamentos",
            "notificaciones", "suscripciones", "dispositivos", "recordatorios_enviados", "solicitudes_actividad",
            "chat_sesiones", "chat_mensajes", "pagos", "grupo_mensajes");

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        List<String> problemas = new ArrayList<>();
        try {
            List<String> existentes = jdbc.queryForList(
                    "select lower(table_name) from information_schema.tables where table_schema = database()", String.class);
            for (String t : TABLAS) {
                if (!existentes.contains(t)) problemas.add("Falta la tabla «" + t + "»");
            }
            if (!existentes.contains("suscripciones") || columnaExiste("suscripciones", "fecha_fin")) {
                // ok
            } else {
                problemas.add("Falta la columna «suscripciones.fecha_fin»");
            }
            if (existentes.contains("suscripciones") && !columnaExiste("suscripciones", "origen")) {
                problemas.add("Falta la columna «suscripciones.origen» (ejecuta cicam_v2_actualizacion_7_planes.sql)");
            }
            if (existentes.contains("grupos") && !columnaNullable("grupos", "adulto_mayor_id")) {
                problemas.add("La columna «grupos.adulto_mayor_id» debe aceptar valores vacíos");
            }
            if (existentes.contains("codigos_acceso") && !columnaNullable("codigos_acceso", "fecha_expira")) {
                problemas.add("La columna «codigos_acceso.fecha_expira» debe aceptar valores vacíos");
            }
        } catch (RuntimeException e) {
            return; // si no se puede consultar el esquema, no se bloquea el arranque
        }
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("""

                    ============================================================
                    LA BASE DE DATOS NO ESTÁ AL DÍA con esta versión de CICAM:
                     - %s

                    SOLUCIÓN: ejecuta de nuevo el script completo «cicam_v2_bd.sql» en MySQL Workbench
                    (recrea la base cicam_v3 desde cero y borra sus datos de prueba), o ejecuta los scripts
                    «cicam_v2_actualizacion_*.sql» que falten (la última es cicam_v2_actualizacion_8_chat_grupo.sql). Luego reinicia el servidor.
                    ============================================================
                    """.formatted(String.join("\n - ", problemas)));
        }
    }

    private boolean columnaExiste(String tabla, String columna) {
        Integer n = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() "
                + "and lower(table_name) = ? and lower(column_name) = ?", Integer.class, tabla, columna);
        return n != null && n > 0;
    }

    private boolean columnaNullable(String tabla, String columna) {
        List<String> r = jdbc.queryForList("select is_nullable from information_schema.columns where table_schema = database() "
                + "and lower(table_name) = ? and lower(column_name) = ?", String.class, tabla, columna);
        return r.isEmpty() || "YES".equalsIgnoreCase(r.get(0));
    }
}
