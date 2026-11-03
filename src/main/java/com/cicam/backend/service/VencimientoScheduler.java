package com.cicam.backend.service;

import com.cicam.backend.model.Actividad;
import com.cicam.backend.model.EstadoActividad;
import com.cicam.backend.model.Grupo;
import com.cicam.backend.model.OrigenCambio;
import com.cicam.backend.model.Rol;
import com.cicam.backend.repository.ActividadRepository;
import com.cicam.backend.repository.GrupoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Identifica automáticamente las actividades vencidas (PENDIENTE con fecha límite pasada) y avisa. */
@Component
@RequiredArgsConstructor
public class VencimientoScheduler {

    private final ActividadRepository actividades;
    private final GrupoRepository grupos;
    private final ActividadService actividadService;
    private final NotificacionService notificaciones;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void marcarVencidas() {
        for (Actividad a : actividades.findByEstadoAndFechaLimiteBeforeAndEliminadaFalse(
                EstadoActividad.PENDIENTE, LocalDateTime.now())) {
            a.setEstado(EstadoActividad.VENCIDA);
            // origen SISTEMA y sin autor: se diferencia de las confirmaciones manuales
            actividadService.registrarHistorial(a, EstadoActividad.PENDIENTE, EstadoActividad.VENCIDA,
                    OrigenCambio.SISTEMA, null);

            Grupo g = grupos.findByAdultoMayorId(a.getAdultoMayor().getId()).orElse(null);
            if (g == null) continue;
            String mensaje = a.getDescripcion() + " de " + a.getAdultoMayor().getNombre()
                    + " venció el " + Textos.fecha(a.getFechaLimite());
            // Familiares del grupo + cuidador responsable (si no tiene, todos los cuidadores)
            notificaciones.notificarRol(g.getId(), Rol.FAMILIAR, null, "Actividad vencida", mensaje);
            if (a.getCuidadorResponsable() != null) {
                notificaciones.notificar(a.getCuidadorResponsable(), "Actividad vencida", mensaje);
            } else {
                notificaciones.notificarRol(g.getId(), Rol.CUIDADOR, null, "Actividad vencida", mensaje);
            }
        }
    }
}
