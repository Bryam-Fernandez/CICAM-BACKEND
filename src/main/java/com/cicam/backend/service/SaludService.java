package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.*;
import com.cicam.backend.dto.Mapeador;
import com.cicam.backend.model.*;
import com.cicam.backend.repository.CitaRepository;
import com.cicam.backend.repository.MedicamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Medicamentos y citas médicas. Solo registro y consulta de información.
 * El FAMILIAR administra; el CUIDADOR y el ADULTO_MAYOR solo consultan.
 */
@Service
@RequiredArgsConstructor
public class SaludService {

    private final MedicamentoRepository medicamentos;
    private final CitaRepository citas;
    private final AccesoService acceso;
    private final NotificacionService notificaciones;
    private final com.cicam.backend.repository.CuidadorPermisoRepository permisos;

    // ---------- Medicamentos ----------

    @Transactional(readOnly = true)
    public List<MedicamentoResponse> listarMedicamentos(Long usuarioId, Long grupoId) {
        Grupo g = acceso.grupoParaIntegrante(grupoId, usuarioId);
        if (g.getAdultoMayor() == null) return List.of();
        return medicamentos.findByAdultoMayorId(g.getAdultoMayor().getId()).stream().map(Mapeador::medicamento).toList();
    }

    @Transactional
    public MedicamentoResponse crearMedicamento(Long familiarId, MedicamentoRequest r) {
        Grupo g = acceso.grupoParaIntegrante(exigirGrupo(r.grupoId()), familiarId);
        autorizarEdicion(familiarId, g, "GESTIONAR_MEDICAMENTOS");
        acceso.exigirAdulto(g);
        Medicamento m = new Medicamento();
        m.setAdultoMayor(g.getAdultoMayor());
        copiar(m, r);
        medicamentos.save(m);
        return Mapeador.medicamento(m);
    }

    @Transactional
    public MedicamentoResponse actualizarMedicamento(Long familiarId, Long id, MedicamentoRequest r) {
        Medicamento m = medicamentos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado"));
        autorizarEdicion(familiarId, acceso.grupoDeAdulto(m.getAdultoMayor().getId()), "GESTIONAR_MEDICAMENTOS");
        copiar(m, r);
        return Mapeador.medicamento(m);
    }

    @Transactional
    public void eliminarMedicamento(Long familiarId, Long id) {
        Medicamento m = medicamentos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado"));
        autorizarEdicion(familiarId, acceso.grupoDeAdulto(m.getAdultoMayor().getId()), "GESTIONAR_MEDICAMENTOS");
        medicamentos.delete(m);
    }

    // ---------- Citas ----------

    @Transactional(readOnly = true)
    public List<CitaResponse> listarCitas(Long usuarioId, Long grupoId) {
        Grupo g = acceso.grupoParaIntegrante(grupoId, usuarioId);
        if (g.getAdultoMayor() == null) return List.of();
        return citas.findByAdultoMayorIdOrderByFechaHoraAsc(g.getAdultoMayor().getId()).stream().map(Mapeador::cita).toList();
    }

    @Transactional
    public CitaResponse crearCita(Long familiarId, CitaRequest r) {
        Grupo g = acceso.grupoParaIntegrante(exigirGrupo(r.grupoId()), familiarId);
        autorizarEdicion(familiarId, g, "GESTIONAR_CITAS");
        acceso.exigirAdulto(g);
        exigirFutura(r.fechaHora());
        Cita c = new Cita();
        c.setAdultoMayor(g.getAdultoMayor());
        copiar(c, r);
        c.setEstado(EstadoCita.PROGRAMADA);
        citas.save(c);
        notificaciones.notificarIntegrantes(g.getId(), familiarId, "Nueva cita médica", detalle(g, c));
        return Mapeador.cita(c);
    }

    /**
     * Si cambia la fecha/hora o el lugar, la cita pasa a REPROGRAMADA. Se avisa a todos los integrantes
     * cuando se reprograma o se cancela.
     */
    @Transactional
    public CitaResponse actualizarCita(Long familiarId, Long id, CitaRequest r) {
        Cita c = citas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada"));
        Grupo g = acceso.grupoDeAdulto(c.getAdultoMayor().getId());

        autorizarEdicion(familiarId, g, "GESTIONAR_CITAS");
        EstadoCita antes = c.getEstado();
        LocalDateTime fechaAntes = c.getFechaHora();
        if (!fechaAntes.equals(r.fechaHora())) {
            exigirFutura(r.fechaHora()); // solo se valida si se cambia la fecha
        }
        String lugarAntes = c.getLugar();
        copiar(c, r);

        EstadoCita nuevo = r.estado() != null ? r.estado() : antes;
        boolean cambioProgramacion = !fechaAntes.equals(c.getFechaHora()) || !lugarAntes.equals(c.getLugar());
        if (nuevo == EstadoCita.PROGRAMADA && cambioProgramacion) {
            nuevo = EstadoCita.REPROGRAMADA;
        }
        c.setEstado(nuevo);

        if (nuevo == EstadoCita.CANCELADA && antes != EstadoCita.CANCELADA) {
            notificaciones.notificarIntegrantes(g.getId(), familiarId, "Cita médica cancelada", detalle(g, c));
        } else if (nuevo != EstadoCita.CANCELADA
                && (cambioProgramacion || (nuevo == EstadoCita.REPROGRAMADA && antes != EstadoCita.REPROGRAMADA))) {
            notificaciones.notificarIntegrantes(g.getId(), familiarId, "Cita médica reprogramada", detalle(g, c));
        }
        return Mapeador.cita(c);
    }

    @Transactional
    public void eliminarCita(Long familiarId, Long id) {
        Cita c = citas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada"));
        acceso.grupoDeAdulto(c.getAdultoMayor().getId());
        autorizarEdicion(familiarId, acceso.grupoDeAdulto(c.getAdultoMayor().getId()), "GESTIONAR_CITAS");
        citas.delete(c);
    }

    private void autorizarEdicion(Long usuarioId, Grupo grupo, String permiso) {
        if (acceso.usuario(usuarioId).getRol() == Rol.FAMILIAR) {
            acceso.grupoParaFamiliar(grupo.getId(), usuarioId);
            return;
        }
        acceso.grupoParaCuidador(grupo.getId(), usuarioId);
        if (!permisos.findByGrupoIdAndCuidadorIdAndFechaRetiroIsNull(grupo.getId(), usuarioId)
                .stream().anyMatch(p -> p.getPermiso().equals(permiso)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para gestionar este apartado");
    }

    private void validarFoto(String foto) {
        if (foto != null && (foto.length() > 1400000 || !foto.matches("data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fotografía debe ser JPG, PNG o WebP y menor a 1 MB");
    }

    // ---------- auxiliares ----------

    private void exigirFutura(LocalDateTime fechaHora) {
        if (!fechaHora.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha y hora de la cita no pueden ser anteriores a la actual");
        }
    }

    private Long exigirGrupo(Long grupoId) {
        if (grupoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el grupo");
        }
        return grupoId;
    }

    private String detalle(Grupo g, Cita c) {
        return c.getTitulo() + " de " + g.getAdultoMayor().getNombre() + " — "
                + Textos.fecha(c.getFechaHora()) + " en " + c.getLugar();
    }

    private void copiar(Medicamento m, MedicamentoRequest r) {
        m.setNombre(r.nombre().trim());
        m.setDosis(r.dosis().trim());
        validarFoto(r.foto());
        m.setFoto(r.foto());
        m.setHorarios(Horarios.normalizar(r.horarios())); // formato 08:00, 20:00
    }

    private void copiar(Cita c, CitaRequest r) {
        validarFoto(r.foto());
        c.setFoto(r.foto());
        c.setEspecialidad(r.especialidad());
        c.setMedico(r.medico());
        c.setIndicaciones(r.indicaciones());
        c.setTitulo(r.titulo().trim());
        c.setLugar(r.lugar().trim());
        c.setFechaHora(r.fechaHora());
    }
}
