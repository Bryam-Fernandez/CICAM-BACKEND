package com.cicam.backend.dto;

import com.cicam.backend.dto.Dtos.*;
import com.cicam.backend.model.*;

public final class Mapeador {

    private Mapeador() {
    }

    public static UsuarioResumen usuario(Usuario u) {
        return u == null ? null : new UsuarioResumen(u.getId(), u.getNombre(), u.getEmail(), u.getRol());
    }

    public static ActividadResponse actividad(Actividad a, Usuario solicitadoA) {
        return new ActividadResponse(a.getId(), a.getDescripcion(), a.getFechaLimite(), a.getEstado(),
                usuario(a.getCuidadorResponsable()), usuario(a.getCreadaPor()),
                a.getFechaCreacion(), a.getFechaCompletada(), usuario(solicitadoA));
    }

    public static HistorialResponse historial(HistorialActividad h) {
        return new HistorialResponse(h.getId(), h.getActividad().getId(), h.getActividad().getDescripcion(),
                h.getEstadoAnterior(), h.getEstadoNuevo(), h.getFecha(), h.getOrigen(),
                h.getAutor() == null ? "Sistema" : h.getAutor().getNombre());
    }

    public static CitaResponse cita(Cita c) {
        return new CitaResponse(c.getId(), c.getTitulo(), c.getLugar(), c.getFechaHora(), c.getEstado(), c.getEspecialidad(), c.getMedico(), c.getIndicaciones(), c.getFoto());
    }

    public static MedicamentoResponse medicamento(Medicamento m) {
        return new MedicamentoResponse(m.getId(), m.getNombre(), m.getDosis(), m.getHorarios(), m.getFoto());
    }

    public static NotificacionResponse notificacion(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getMensaje(), n.getFecha(), n.isLeida());
    }
}
