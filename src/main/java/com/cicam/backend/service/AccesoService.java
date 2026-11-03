package com.cicam.backend.service;

import com.cicam.backend.model.Grupo;
import com.cicam.backend.model.PlanTipo;
import com.cicam.backend.model.EstadoSuscripcion;
import com.cicam.backend.model.Rol;
import com.cicam.backend.model.Usuario;
import com.cicam.backend.repository.GrupoIntegranteRepository;
import com.cicam.backend.repository.GrupoRepository;
import com.cicam.backend.repository.SuscripcionRepository;
import com.cicam.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Reglas de acceso: quién pertenece a qué grupo y qué rol puede actuar. Todo se valida en el servidor. */
@Service
@RequiredArgsConstructor
public class AccesoService {

    public static final int LIMITE_GRATUITO = 7;
    public static final int LIMITE_PREMIUM = 15;

    private final UsuarioRepository usuarios;
    private final GrupoRepository grupos;
    private final GrupoIntegranteRepository integrantes;
    private final SuscripcionRepository suscripciones;

    /** Consultas al asistente de IA por usuario y por día, según el plan del grupo. */
    @Value("${cicam.plan.ia.consultas-dia-gratuito:5}")
    private int consultasIaGratuito;

    @Value("${cicam.plan.ia.consultas-dia-premium:50}")
    private int consultasIaPremium;

    /** Días de historial de actividades visibles en el plan gratuito (Premium: todo el historial). */
    @Value("${cicam.plan.historial-dias-gratuito:30}")
    private int historialDiasGratuito;

    /** Días de mensajes del chat del grupo visibles en el plan gratuito (Premium: todo el historial). */
    @Value("${cicam.plan.chat-historial-dias-gratuito:7}")
    private int chatDiasGratuito;

    public Usuario usuario(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    public Grupo grupo(Long id) {
        return grupos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado"));
    }

    public Grupo grupoDeAdulto(Long adultoId) {
        return grupos.findByAdultoMayorId(adultoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado"));
    }

    /** El usuario debe ser integrante del grupo (cualquier rol). */
    public Grupo grupoParaIntegrante(Long grupoId, Long usuarioId) {
        Grupo g = grupo(grupoId);
        if (!integrantes.esIntegrante(grupoId, usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a este grupo");
        }
        return g;
    }

    /** El usuario debe ser un FAMILIAR integrante del grupo. */
    public Grupo grupoParaFamiliar(Long grupoId, Long usuarioId) {
        Grupo g = grupoParaIntegrante(grupoId, usuarioId);
        if (usuario(usuarioId).getRol() != Rol.FAMILIAR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un familiar puede realizar esta acción");
        }
        return g;
    }

    /** El usuario debe ser un CUIDADOR integrante del grupo. */
    public Grupo grupoParaCuidador(Long grupoId, Long usuarioId) {
        Grupo g = grupoParaIntegrante(grupoId, usuarioId);
        if (usuario(usuarioId).getRol() != Rol.CUIDADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un cuidador puede realizar esta acción");
        }
        return g;
    }

    /** PREMIUM activo y vigente (no vencido). */
    public boolean tienePremium(Long usuarioId) {
        return suscripciones.contarVigentes(usuarioId, PlanTipo.PREMIUM, EstadoSuscripcion.ACTIVA,
                java.time.LocalDateTime.now()) > 0;
    }

    /** Las ventajas del plan son del grupo: dependen del plan de quien lo creó. */
    public boolean grupoPremium(Grupo g) {
        return tienePremium(g.getCreadoPor().getId());
    }

    public int consultasIaPorDia(boolean premium) {
        return premium ? consultasIaPremium : consultasIaGratuito;
    }

    public int consultasIaPorDiaGratuito() {
        return consultasIaGratuito;
    }

    public int consultasIaPorDiaPremium() {
        return consultasIaPremium;
    }

    /** Días de chat visibles; 0 = sin límite. */
    public int diasChat(boolean premium) {
        return premium ? 0 : chatDiasGratuito;
    }

    /** Días de historial visibles; 0 = sin límite. */
    public int diasHistorial(boolean premium) {
        return premium ? 0 : historialDiasGratuito;
    }

    /** Devuelve el adulto mayor del grupo; si el grupo aún no tiene uno, responde con un error claro. */
    public Usuario exigirAdulto(Grupo g) {
        if (g.getAdultoMayor() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El grupo aún no tiene un adulto mayor. Un familiar debe agregarlo primero");
        }
        return g.getAdultoMayor();
    }

    /** 15 integrantes si quien creó el grupo tiene el plan PREMIUM activo; 7 en caso contrario. */
    public int limiteIntegrantes(Grupo g) {
        return tienePremium(g.getCreadoPor().getId()) ? LIMITE_PREMIUM : LIMITE_GRATUITO;
    }
}
