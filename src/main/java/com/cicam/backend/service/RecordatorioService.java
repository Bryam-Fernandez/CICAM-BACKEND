package com.cicam.backend.service;

import com.cicam.backend.model.*;
import com.cicam.backend.repository.CitaRepository;
import com.cicam.backend.repository.GrupoRepository;
import com.cicam.backend.repository.MedicamentoRepository;
import com.cicam.backend.repository.RecordatorioEnviadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Recordatorios automáticos (se revisan cada minuto):
 * - Citas: 24 horas y 2 horas antes, a todos los integrantes del grupo (con Premium, también 7 días antes).
 * - Medicamentos: a la hora indicada, al adulto mayor y a sus cuidadores.
 * Cada recordatorio se registra en recordatorios_enviados para no repetirse.
 */
@Component
@RequiredArgsConstructor
public class RecordatorioService {

    private static final int TOLERANCIA_CITA_MIN = 10;
    private static final int TOLERANCIA_MEDICAMENTO_MIN = 5;

    private final CitaRepository citas;
    private final MedicamentoRepository medicamentos;
    private final RecordatorioEnviadoRepository enviados;
    private final GrupoRepository grupos;
    private final NotificacionService notificaciones;
    private final AccesoService acceso;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void procesar() {
        LocalDateTime ahora = LocalDateTime.now();

        for (Cita c : citas.findByEstadoNotAndFechaHoraBetween(EstadoCita.CANCELADA, ahora, ahora.plusDays(7).plusHours(1))) {
            recordarCita(c, "CITA_7D", c.getFechaHora().minusDays(7), "en 7 días", ahora, true);
            recordarCita(c, "CITA_24H", c.getFechaHora().minusHours(24), "en 24 horas", ahora, false);
            recordarCita(c, "CITA_2H", c.getFechaHora().minusHours(2), "en 2 horas", ahora, false);
        }

        for (Medicamento m : medicamentos.findAll()) {
            for (LocalTime hora : Horarios.parsear(m.getHorarios())) {
                LocalDateTime programado = LocalDate.now().atTime(hora);
                if (dentroDeVentana(programado, ahora, TOLERANCIA_MEDICAMENTO_MIN)
                        && marcar("MEDICAMENTO", m.getId(), programado)) {
                    recordarMedicamento(m, hora);
                }
            }
        }
    }

    private void recordarCita(Cita c, String tipo, LocalDateTime objetivo, String cuando, LocalDateTime ahora,
                              boolean soloPremium) {
        if (!dentroDeVentana(objetivo, ahora, TOLERANCIA_CITA_MIN)) return;
        Grupo g = grupos.findByAdultoMayorId(c.getAdultoMayor().getId()).orElse(null);
        if (g == null) return;
        if (soloPremium && !acceso.grupoPremium(g)) return;
        if (!marcar(tipo, c.getId(), c.getFechaHora())) return;
        notificaciones.notificarIntegrantes(g.getId(), null, "Recordatorio de cita médica",
                c.getTitulo() + " de " + c.getAdultoMayor().getNombre() + " " + cuando + ": "
                        + Textos.fecha(c.getFechaHora()) + " en " + c.getLugar());
    }

    private void recordarMedicamento(Medicamento m, LocalTime hora) {
        Grupo g = grupos.findByAdultoMayorId(m.getAdultoMayor().getId()).orElse(null);
        if (g == null) return;
        String mensaje = "Hora del medicamento de " + m.getAdultoMayor().getNombre() + ": "
                + m.getNombre() + " (" + m.getDosis() + ") a las " + hora;
        notificaciones.notificar(m.getAdultoMayor(), "Recordatorio de medicamento", mensaje);
        notificaciones.notificarRol(g.getId(), Rol.CUIDADOR, null, "Recordatorio de medicamento", mensaje);
    }

    /** true si ya llegó la hora objetivo y no han pasado más de "minutos" desde entonces. */
    private boolean dentroDeVentana(LocalDateTime objetivo, LocalDateTime ahora, int minutos) {
        return !objetivo.isAfter(ahora) && ahora.isBefore(objetivo.plusMinutes(minutos));
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
