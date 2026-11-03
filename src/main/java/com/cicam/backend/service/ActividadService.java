package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.ActividadRequest;
import com.cicam.backend.dto.Dtos.ActividadResponse;
import com.cicam.backend.dto.Dtos.HistorialResponse;
import com.cicam.backend.dto.Dtos.SolicitudResponse;
import com.cicam.backend.dto.Mapeador;
import com.cicam.backend.model.*;
import com.cicam.backend.repository.ActividadRepository;
import com.cicam.backend.repository.GrupoIntegranteRepository;
import com.cicam.backend.repository.HistorialActividadRepository;
import com.cicam.backend.repository.SolicitudActividadRepository;
import com.cicam.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reglas de actividades:
 * - El FAMILIAR crea, modifica y elimina (borrado lógico) actividades.
 * - El responsable puede ser un CUIDADOR (asignación directa) o un FAMILIAR del grupo. A otro familiar se le
 *   envía una SOLICITUD: la actividad se crea aunque la rechace; si la acepta, queda como su responsable.
 *   Un familiar también puede tomar por sí mismo una actividad sin responsable.
 * - Si el responsable es un familiar, solo ese familiar puede marcarla como completada.
 * - Si no, la completan los familiares, o el cuidador responsable (o cualquier cuidador si no tiene responsable).
 * - El CUIDADOR ve las asignadas a él y las sin responsable (salvo las que esperan respuesta de un familiar).
 * - El ADULTO_MAYOR solo consulta.
 */
@Service
@RequiredArgsConstructor
public class ActividadService {

    private final ActividadRepository actividades;
    private final HistorialActividadRepository historial;
    private final SolicitudActividadRepository solicitudes;
    private final UsuarioRepository usuarios;
    private final GrupoIntegranteRepository integrantes;
    private final AccesoService acceso;
    private final NotificacionService notificaciones;

    // ---------- FAMILIAR: crear, modificar, eliminar, tomar ----------

    @Transactional
    public ActividadResponse crear(Long familiarId, ActividadRequest r) {
        if (r.grupoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el grupo");
        }
        if (!r.fechaLimite().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha límite debe ser posterior a la actual");
        }
        Grupo g = acceso.grupoParaFamiliar(r.grupoId(), familiarId);
        acceso.exigirAdulto(g);
        Usuario familiar = acceso.usuario(familiarId);
        Usuario destino = destino(g, r.cuidadorResponsableId());
        boolean pideSolicitud = requiereSolicitud(destino, familiarId);

        Actividad a = new Actividad();
        a.setDescripcion(r.descripcion().trim());
        a.setFechaLimite(r.fechaLimite());
        a.setAdultoMayor(g.getAdultoMayor());
        a.setCuidadorResponsable(pideSolicitud ? null : destino); // a un familiar ajeno solo se le pide
        a.setCreadaPor(familiar);
        actividades.save(a);
        registrarHistorial(a, null, EstadoActividad.PENDIENTE, OrigenCambio.MANUAL, familiar);

        if (pideSolicitud) {
            enviarSolicitud(g, a, destino, familiar);
        } else if (destino == null) {
            avisarSinAsignar(g, a);
        } else if (destino.getRol() == Rol.CUIDADOR) {
            avisarAsignado(g, a, destino);
        }
        return mapear(a);
    }

    @Transactional
    public ActividadResponse actualizar(Long familiarId, Long id, ActividadRequest r) {
        Actividad a = buscar(id);
        Grupo g = acceso.grupoDeAdulto(a.getAdultoMayor().getId());
        Usuario familiar = acceso.usuario(familiarId);
        acceso.grupoParaFamiliar(g.getId(), familiarId);
        if (a.getEstado() == EstadoActividad.COMPLETADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede modificar una actividad completada");
        }
        Usuario destino = destino(g, r.cuidadorResponsableId());
        Long anteriorId = a.getCuidadorResponsable() == null ? null : a.getCuidadorResponsable().getId();
        SolicitudActividad pendiente = solicitudes
                .findFirstByActividadIdAndEstado(a.getId(), EstadoSolicitud.PENDIENTE).orElse(null);

        a.setDescripcion(r.descripcion().trim());
        a.setFechaLimite(r.fechaLimite());

        boolean sinCambio = destino != null && (destino.getId().equals(anteriorId)
                || (pendiente != null && pendiente.getFamiliar().getId().equals(destino.getId())));
        if (!sinCambio) {
            boolean teniaAsignacion = anteriorId != null || pendiente != null;
            cancelarPendientes(a);
            boolean pideSolicitud = requiereSolicitud(destino, familiarId);
            a.setCuidadorResponsable(pideSolicitud ? null : destino);
            if (pideSolicitud) {
                enviarSolicitud(g, a, destino, familiar);
            } else if (destino == null) {
                if (teniaAsignacion) avisarSinAsignar(g, a);
            } else if (destino.getRol() == Rol.CUIDADOR) {
                avisarAsignado(g, a, destino);
            }
        }

        // Si una actividad vencida recibe una nueva fecha futura vuelve a PENDIENTE (queda en el historial).
        if (a.getEstado() == EstadoActividad.VENCIDA && r.fechaLimite().isAfter(LocalDateTime.now())) {
            a.setEstado(EstadoActividad.PENDIENTE);
            registrarHistorial(a, EstadoActividad.VENCIDA, EstadoActividad.PENDIENTE, OrigenCambio.MANUAL, familiar);
        }
        return mapear(a);
    }

    @Transactional
    public void eliminar(Long familiarId, Long id) {
        Actividad a = buscar(id);
        Grupo g = acceso.grupoDeAdulto(a.getAdultoMayor().getId());
        acceso.grupoParaFamiliar(g.getId(), familiarId);
        cancelarPendientes(a);
        a.setEliminada(true);
        a.setFechaEliminacion(LocalDateTime.now());
    }

    /** Un familiar toma por sí mismo una actividad sin responsable. */
    @Transactional
    public ActividadResponse tomar(Long familiarId, Long id) {
        Actividad a = buscar(id);
        Grupo g = acceso.grupoDeAdulto(a.getAdultoMayor().getId());
        acceso.grupoParaFamiliar(g.getId(), familiarId);
        Usuario familiar = acceso.usuario(familiarId);
        if (a.getEstado() == EstadoActividad.COMPLETADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La actividad ya fue completada");
        }
        if (a.getCuidadorResponsable() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La actividad ya tiene responsable");
        }
        cancelarPendientes(a);
        a.setCuidadorResponsable(familiar);
        notificaciones.notificarRol(g.getId(), Rol.CUIDADOR, null, "Actividad tomada",
                familiar.getNombre() + " (familiar) tomó la actividad de " + g.getAdultoMayor().getNombre()
                        + ": " + a.getDescripcion());
        return mapear(a);
    }

    // ---------- Solicitudes a familiares ----------

    @Transactional(readOnly = true)
    public List<SolicitudResponse> solicitudesPendientes(Long familiarId) {
        return solicitudes.findByFamiliarIdAndEstadoOrderByFechaSolicitudDesc(familiarId, EstadoSolicitud.PENDIENTE)
                .stream()
                .filter(s -> !s.getActividad().isEliminada())
                .map(s -> new SolicitudResponse(s.getId(), s.getActividad().getId(), s.getActividad().getDescripcion(),
                        s.getActividad().getAdultoMayor().getNombre(), s.getActividad().getFechaLimite(),
                        s.getSolicitadaPor().getNombre(), s.getFechaSolicitud()))
                .toList();
    }

    /** El familiar acepta (queda como responsable) o rechaza. La actividad ya existe en ambos casos. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void responderSolicitud(Long familiarId, Long solicitudId, boolean aceptar) {
        SolicitudActividad s = solicitudes.findById(solicitudId)
                .filter(x -> x.getFamiliar().getId().equals(familiarId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (s.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya fue respondida o cancelada");
        }
        Actividad a = s.getActividad();
        if (a.isEliminada() || a.getEstado() == EstadoActividad.COMPLETADA || a.getCuidadorResponsable() != null) {
            s.setEstado(EstadoSolicitud.CANCELADA);
            s.setFechaRespuesta(LocalDateTime.now());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La actividad ya no está disponible");
        }
        Usuario familiar = acceso.usuario(familiarId);
        Grupo g = acceso.grupoDeAdulto(a.getAdultoMayor().getId());
        s.setFechaRespuesta(LocalDateTime.now());
        if (aceptar) {
            s.setEstado(EstadoSolicitud.ACEPTADA);
            a.setCuidadorResponsable(familiar);
            notificaciones.notificar(s.getSolicitadaPor(), "Solicitud aceptada",
                    familiar.getNombre() + " aceptó encargarse de: " + a.getDescripcion());
        } else {
            s.setEstado(EstadoSolicitud.RECHAZADA);
            notificaciones.notificar(s.getSolicitadaPor(), "Solicitud rechazada",
                    familiar.getNombre() + " no puede encargarse de: " + a.getDescripcion()
                            + ". La actividad quedó sin asignar.");
            avisarSinAsignar(g, a);
        }
    }

    // ---------- Completar ----------

    /**
     * - Responsable familiar: solo ese familiar puede completarla.
     * - Responsable cuidador: él o cualquier familiar.
     * - Sin responsable: cualquier familiar, o un cuidador (que queda como responsable).
     */
    @Transactional
    public ActividadResponse completar(Long usuarioId, Long id) {
        Actividad a = buscar(id);
        Grupo g = acceso.grupoDeAdulto(a.getAdultoMayor().getId());
        acceso.grupoParaIntegrante(g.getId(), usuarioId);
        Usuario quien = acceso.usuario(usuarioId);
        if (quien.getRol() == Rol.ADULTO_MAYOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El adulto mayor no puede confirmar actividades");
        }
        if (a.getEstado() == EstadoActividad.COMPLETADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La actividad ya estaba completada");
        }
        Usuario resp = a.getCuidadorResponsable();
        if (resp != null && resp.getRol() == Rol.FAMILIAR) {
            if (!resp.getId().equals(usuarioId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Solo " + resp.getNombre() + " (familiar responsable) puede completar esta actividad");
            }
        } else if (quien.getRol() == Rol.CUIDADOR) {
            if (resp != null && !resp.getId().equals(usuarioId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La actividad está asignada a otro cuidador");
            }
            if (resp == null) {
                if (solicitudes.findFirstByActividadIdAndEstado(a.getId(), EstadoSolicitud.PENDIENTE).isPresent()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "La actividad está esperando la respuesta de un familiar");
                }
                a.setCuidadorResponsable(quien);
            }
        }
        cancelarPendientes(a);
        EstadoActividad anterior = a.getEstado();
        a.setEstado(EstadoActividad.COMPLETADA);
        a.setFechaCompletada(LocalDateTime.now());
        registrarHistorial(a, anterior, EstadoActividad.COMPLETADA, OrigenCambio.MANUAL, quien);

        String mensaje = quien.getNombre() + " completó para " + g.getAdultoMayor().getNombre() + ": " + a.getDescripcion();
        notificaciones.notificarRol(g.getId(), Rol.FAMILIAR, usuarioId, "Actividad completada", mensaje);
        notificaciones.notificarRol(g.getId(), Rol.CUIDADOR, usuarioId, "Actividad completada", mensaje);
        return mapear(a);
    }

    // ---------- CONSULTAS ----------

    /** Filtros opcionales: estado, responsable (o sin asignar) y fecha límite. */
    @Transactional(readOnly = true)
    public List<ActividadResponse> listar(Long usuarioId, Long grupoId, EstadoActividad estado,
                                          Long responsableId, boolean sinAsignar, LocalDate fecha) {
        Grupo g = acceso.grupoParaIntegrante(grupoId, usuarioId);
        if (g.getAdultoMayor() == null) return List.of();
        Usuario u = acceso.usuario(usuarioId);
        return visibles(u, g)
                .stream()
                .filter(a -> estado == null || a.getEstado() == estado)
                .filter(a -> responsableId == null
                        || (a.getCuidadorResponsable() != null && a.getCuidadorResponsable().getId().equals(responsableId)))
                .filter(a -> !sinAsignar || a.getCuidadorResponsable() == null)
                .filter(a -> fecha == null || a.getFechaLimite().toLocalDate().equals(fecha))
                .map(this::mapear)
                .toList();
    }

    @Transactional(readOnly = true)
    public ActividadResponse obtener(Long usuarioId, Long id) {
        Actividad a = buscar(id);
        Grupo g = acceso.grupoDeAdulto(a.getAdultoMayor().getId());
        acceso.grupoParaIntegrante(g.getId(), usuarioId);
        if (!visible(acceso.usuario(usuarioId), a)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a esta actividad");
        }
        return mapear(a);
    }

    /** Historial del grupo: lo ven los familiares y el adulto mayor (no los cuidadores). Filtros: autor y fecha. */
    @Transactional(readOnly = true)
    public List<HistorialResponse> historial(Long usuarioId, Long grupoId, Long autorId, LocalDate fecha) {
        Grupo g = acceso.grupoParaIntegrante(grupoId, usuarioId);
        if (g.getAdultoMayor() == null) return List.of();
        if (acceso.usuario(usuarioId).getRol() == Rol.CUIDADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El historial no está disponible para cuidadores");
        }
        int dias = acceso.diasHistorial(acceso.grupoPremium(g)); // plan gratuito: solo los últimos días
        LocalDateTime desde = dias > 0 ? LocalDateTime.now().minusDays(dias) : null;
        return historial.porAdulto(g.getAdultoMayor().getId()).stream()
                .filter(h -> desde == null || !h.getFecha().isBefore(desde))
                .filter(h -> autorId == null || (h.getAutor() != null && h.getAutor().getId().equals(autorId)))
                .filter(h -> fecha == null || h.getFecha().toLocalDate().equals(fecha))
                .map(Mapeador::historial)
                .toList();
    }

    /** Actividades del grupo que el usuario puede ver (sin las eliminadas), por fecha límite. Usado también por el asistente. */
    @Transactional(readOnly = true)
    public List<Actividad> visibles(Usuario u, Grupo g) {
        if (g.getAdultoMayor() == null) return List.of();
        return actividades.findByAdultoMayorIdAndEliminadaFalseOrderByFechaLimiteAsc(g.getAdultoMayor().getId())
                .stream().filter(a -> visible(u, a)).toList();
    }

    // ---------- usados también por el vencimiento automático ----------

    public void registrarHistorial(Actividad a, EstadoActividad anterior, EstadoActividad nuevo,
                                   OrigenCambio origen, Usuario autor) {
        HistorialActividad h = new HistorialActividad();
        h.setActividad(a);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setOrigen(origen);
        h.setAutor(autor);
        historial.save(h);
    }

    // ---------- auxiliares ----------

    private ActividadResponse mapear(Actividad a) {
        Usuario solicitado = solicitudes.findFirstByActividadIdAndEstado(a.getId(), EstadoSolicitud.PENDIENTE)
                .map(SolicitudActividad::getFamiliar).orElse(null);
        return Mapeador.actividad(a, solicitado);
    }

    private Actividad buscar(Long id) {
        return actividades.findById(id)
                .filter(a -> !a.isEliminada())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Actividad no encontrada"));
    }

    /** Familiar y adulto mayor ven todo; el cuidador, lo asignado a él y lo sin responsable que no espera respuesta. */
    private boolean visible(Usuario u, Actividad a) {
        if (u.getRol() != Rol.CUIDADOR) return true;
        if (a.getCuidadorResponsable() != null) return a.getCuidadorResponsable().getId().equals(u.getId());
        return !solicitudes.findFirstByActividadIdAndEstado(a.getId(), EstadoSolicitud.PENDIENTE).isPresent();
    }

    /** El responsable puede ser un cuidador o un familiar integrante del grupo. */
    private Usuario destino(Grupo g, Long usuarioId) {
        if (usuarioId == null) return null;
        Usuario u = usuarios.findById(usuarioId)
                .filter(x -> x.getRol() == Rol.CUIDADOR || x.getRol() == Rol.FAMILIAR)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El responsable debe ser un cuidador o un familiar"));
        if (!integrantes.esIntegrante(g.getId(), u.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El responsable no pertenece al grupo");
        }
        return u;
    }

    /** A un cuidador se le asigna directamente; a otro familiar se le envía una solicitud. */
    private boolean requiereSolicitud(Usuario destino, Long actorId) {
        return destino != null && destino.getRol() == Rol.FAMILIAR && !destino.getId().equals(actorId);
    }

    private void cancelarPendientes(Actividad a) {
        for (SolicitudActividad s : solicitudes.findByActividadIdAndEstado(a.getId(), EstadoSolicitud.PENDIENTE)) {
            s.setEstado(EstadoSolicitud.CANCELADA);
            s.setFechaRespuesta(LocalDateTime.now());
        }
    }

    private void enviarSolicitud(Grupo g, Actividad a, Usuario familiar, Usuario solicitante) {
        cancelarPendientes(a);
        SolicitudActividad s = new SolicitudActividad();
        s.setActividad(a);
        s.setFamiliar(familiar);
        s.setSolicitadaPor(solicitante);
        solicitudes.save(s);
        notificaciones.notificar(familiar, "Solicitud de actividad",
                solicitante.getNombre() + " te pide encargarte de: " + detalle(g, a));
    }

    private void avisarSinAsignar(Grupo g, Actividad a) {
        notificaciones.notificarRol(g.getId(), Rol.CUIDADOR, null, "Actividad sin asignar",
                "Hay una actividad sin asignar para " + g.getAdultoMayor().getNombre() + ": "
                        + a.getDescripcion() + " (límite " + Textos.fecha(a.getFechaLimite()) + ")");
    }

    private void avisarAsignado(Grupo g, Actividad a, Usuario cuidador) {
        notificaciones.notificar(cuidador, "Nueva actividad asignada", "Se te asignó para " + detalle(g, a));
    }

    private String detalle(Grupo g, Actividad a) {
        return a.getDescripcion() + " de " + g.getAdultoMayor().getNombre()
                + " (límite " + Textos.fecha(a.getFechaLimite()) + ")";
    }
}
