package com.cicam.backend.service;

import com.cicam.backend.model.*;
import com.cicam.backend.repository.ActividadRepository;
import com.cicam.backend.repository.GrupoRepository;
import com.cicam.backend.repository.RecordatorioEnviadoRepository;
import com.cicam.backend.repository.SuscripcionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Avisos automáticos ligados a los planes:
 * - Resumen semanal del cuidado (ventaja PREMIUM): cada lunes a las 8:00 a los familiares del grupo.
 * - Fin de la prueba gratuita: el último día de la prueba, a las 9:00, se avisa al familiar para que decida.
 * Cada aviso se registra en recordatorios_enviados para no repetirse.
 */
@Component
@RequiredArgsConstructor
public class AvisosPlanService {

    private final GrupoRepository grupos;
    private final ActividadRepository actividades;
    private final SuscripcionRepository suscripciones;
    private final RecordatorioEnviadoRepository enviados;
    private final NotificacionService notificaciones;
    private final AccesoService acceso;

    @Scheduled(cron = "0 0 8 * * MON", zone = "America/Lima")
    @Transactional
    public void resumenSemanal() {
        LocalDateTime desde = LocalDateTime.now().minusDays(7);
        LocalDateTime lunes = LocalDate.now().atStartOfDay();
        for (Grupo g : grupos.findAll()) {
            if (g.getAdultoMayor() == null || !acceso.grupoPremium(g)) continue;
            List<Actividad> todas = actividades
                    .findByAdultoMayorIdAndEliminadaFalseOrderByFechaLimiteAsc(g.getAdultoMayor().getId());
            long completadas = todas.stream()
                    .filter(a -> a.getEstado() == EstadoActividad.COMPLETADA
                            && a.getFechaCompletada() != null && a.getFechaCompletada().isAfter(desde))
                    .count();
            long vencidas = todas.stream()
                    .filter(a -> a.getEstado() == EstadoActividad.VENCIDA && a.getFechaLimite().isAfter(desde))
                    .count();
            long pendientes = todas.stream().filter(a -> a.getEstado() == EstadoActividad.PENDIENTE).count();
            if (completadas + vencidas + pendientes == 0) continue;
            if (!marcar("RESUMEN_SEMANAL", g.getId(), lunes)) continue;
            notificaciones.notificarRol(g.getId(), Rol.FAMILIAR, null,
                    "Resumen semanal de " + g.getAdultoMayor().getNombre(),
                    "Última semana: " + completadas + " actividades completadas y " + vencidas
                            + " vencidas. Pendientes ahora: " + pendientes + ".");
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Lima")
    @Transactional
    public void avisarFinDePrueba() {
        LocalDateTime ahora = LocalDateTime.now();
        for (Suscripcion s : suscripciones.pruebasPorVencer(EstadoSuscripcion.ACTIVA, ahora, ahora.plusHours(24))) {
            if (!marcar("PRUEBA_FIN", s.getId(), s.getFechaFin())) continue;
            notificaciones.notificar(s.getUsuario(), "Tu prueba de Premium termina pronto",
                    "Termina el " + Textos.fecha(s.getFechaFin()) + ". Activa Premium desde Planes para seguir con "
                            + "tus grupos y ventajas; si no, pasarás al plan Gratuito y conservas todos tus datos.");
        }
    }

    /** Registra el envío; devuelve false si ya se había enviado. */
    private boolean marcar(String tipo, Long referenciaId, LocalDateTime ocurrencia) {
        if (enviados.existsByTipoAndReferenciaIdAndOcurrencia(tipo, referenciaId, ocurrencia)) return false;
        RecordatorioEnviado r = new RecordatorioEnviado();
        r.setTipo(tipo);
        r.setReferenciaId(referenciaId);
        r.setOcurrencia(ocurrencia);
        enviados.save(r);
        return true;
    }
}
