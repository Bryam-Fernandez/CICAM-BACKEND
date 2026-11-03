package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.*;
import com.cicam.backend.dto.Mapeador;
import com.cicam.backend.model.*;
import com.cicam.backend.repository.GrupoIntegranteRepository;
import com.cicam.backend.repository.GrupoRepository;
import com.cicam.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Un grupo = 1 adulto mayor + sus familiares + sus cuidadores.
 * Primero un familiar CREA el grupo y después AGREGA al adulto mayor (son pasos separados).
 * Límite de integrantes: 7 (GRATUITO) o 15 (PREMIUM). Límite de grupos por familiar: configurable por plan.
 */
@Service
@RequiredArgsConstructor
public class GrupoService {

    private final UsuarioRepository usuarios;
    private final GrupoRepository grupos;
    private final GrupoIntegranteRepository integrantes;
    private final AccesoService acceso;
    private final CodigoAccesoService codigos;

    @Value("${cicam.plan.max-grupos-gratuito:2}")
    private int maxGruposGratuito;

    @Value("${cicam.plan.max-grupos-premium:5}")
    private int maxGruposPremium;

    /** Un familiar crea un grupo (todavía sin adulto mayor). Respeta el límite de grupos de su plan. */
    @Transactional
    public GrupoResponse crearGrupo(Long familiarId, String nombre) {
        Usuario familiar = acceso.usuario(familiarId);
        if (familiar.getRol() != Rol.FAMILIAR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un familiar puede crear un grupo");
        }
        int limiteGrupos = acceso.tienePremium(familiarId) ? maxGruposPremium : maxGruposGratuito;
        if (grupos.countByCreadoPorId(familiarId) >= limiteGrupos) {
            boolean premium = acceso.tienePremium(familiarId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, premium
                    ? "Alcanzaste el límite de " + limiteGrupos + " grupos del plan Premium"
                    : "El plan Gratuito permite " + limiteGrupos + (limiteGrupos == 1 ? " grupo" : " grupos")
                    + ". Con Premium puedes crear hasta " + maxGruposPremium
                    + " (desde Planes puedes probarlo gratis)");
        }
        Grupo g = new Grupo();
        g.setNombre(nombre.trim());
        g.setCreadoPor(familiar);
        grupos.save(g);
        agregar(g, familiar, familiar);
        return respuesta(g);
    }

    @Transactional
    public GrupoResponse renombrar(Long usuarioId, Long grupoId, String nombre) {
        Grupo g = grupoDeCreador(grupoId, usuarioId);
        if (nombre == null || nombre.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el nombre");
        g.setNombre(nombre.trim());
        return respuesta(g);
    }

    @Transactional
    public void eliminar(Long usuarioId, Long grupoId) {
        Grupo g = grupoDeCreador(grupoId, usuarioId);
        // Evita perder registros médicos, actividades e historial asociados al adulto mayor.
        if (g.getAdultoMayor() != null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Este grupo contiene un adulto mayor. No se puede eliminar sin gestionar antes sus datos asociados");
        integrantes.deleteAll(integrantes.integrantes(grupoId));
        integrantes.flush();
        grupos.delete(g);
    }

    private Grupo grupoDeCreador(Long grupoId, Long usuarioId) {
        Grupo g = acceso.grupoParaIntegrante(grupoId, usuarioId);
        if (!g.getCreadoPor().getId().equals(usuarioId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Solo el creador del grupo puede realizar esta acción");
        return g;
    }

    /** El familiar agrega al adulto mayor al grupo: se crea su perfil y su código de acceso. */
    @Transactional
    public AdultoRegistradoResponse agregarAdulto(Long familiarId, Long grupoId, AdultoRequest r) {
        Grupo g = acceso.grupoParaFamiliar(grupoId, familiarId);
        Usuario familiar = acceso.usuario(familiarId);
        if (g.getAdultoMayor() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este grupo ya tiene un adulto mayor");
        }
        if (!r.consentimientoDatos()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes confirmar que cuentas con la autorización del adulto mayor para tratar sus datos personales (Ley N.º 29733)");
        }
        Usuario adulto = new Usuario();
        adulto.setNombre(r.nombre().trim());
        if (usuarios.existsByDni(r.dni())) throw new ResponseStatusException(HttpStatus.CONFLICT, "El DNI ya está registrado");
        adulto.setDni(r.dni());
        adulto.setRol(Rol.ADULTO_MAYOR);
        adulto.setFechaNacimiento(r.fechaNacimiento());
        adulto.setConsentimientoDatos(true);
        adulto.setRegistradoPor(familiar);
        usuarios.save(adulto);

        g.setAdultoMayor(adulto);
        grupos.saveAndFlush(g);
        agregar(g, adulto, familiar);

        CodigoResponse codigo = codigos.generar(adulto, familiar);
        return new AdultoRegistradoResponse(respuesta(g), codigo);
    }

    @Transactional(readOnly = true)
    public List<GrupoResponse> listar(Long usuarioId) {
        return integrantes.gruposDe(usuarioId).stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public List<IntegranteResponse> listarIntegrantes(Long usuarioId, Long grupoId) {
        acceso.grupoParaIntegrante(grupoId, usuarioId);
        return integrantes.integrantes(grupoId).stream()
                .map(gi -> new IntegranteResponse(Mapeador.usuario(gi.getUsuario()), gi.getFechaIngreso()))
                .toList();
    }

    /** Solo un familiar del grupo agrega integrantes (familiares o cuidadores) por correo. */
    @Transactional
    public IntegranteResponse agregarIntegrante(Long familiarId, Long grupoId, String email) {
        Grupo g = acceso.grupoParaFamiliar(grupoId, familiarId);
        Usuario nuevo = usuarios.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe un usuario registrado con ese correo"));
        if (nuevo.getRol() == Rol.ADULTO_MAYOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se pueden agregar familiares o cuidadores");
        }
        if (integrantes.esIntegrante(grupoId, nuevo.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya es integrante del grupo");
        }
        agregar(g, nuevo, acceso.usuario(familiarId));
        return new IntegranteResponse(Mapeador.usuario(nuevo), LocalDateTime.now());
    }

    /** Nuevo código de acceso. Revoca el anterior y cierra las sesiones del adulto mayor. */
    @Transactional
    public CodigoResponse nuevoCodigo(Long familiarId, Long grupoId) {
        Grupo g = acceso.grupoParaFamiliar(grupoId, familiarId);
        return codigos.generar(acceso.exigirAdulto(g), acceso.usuario(familiarId));
    }

    // ---------- auxiliares ----------

    /** Valida el límite del plan antes de agregar a cualquier integrante. */
    private void agregar(Grupo g, Usuario usuario, Usuario agregadoPor) {
        int limite = acceso.limiteIntegrantes(g);
        if (integrantes.contar(g.getId()) >= limite) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El grupo alcanzó el límite de " + limite + " integrantes de su plan");
        }
        GrupoIntegrante gi = new GrupoIntegrante();
        gi.setId(new GrupoIntegranteId(g.getId(), usuario.getId()));
        gi.setGrupo(g);
        gi.setUsuario(usuario);
        gi.setAgregadoPor(agregadoPor);
        integrantes.save(gi);
    }

    private GrupoResponse respuesta(Grupo g) {
        return new GrupoResponse(g.getId(), g.getNombre(), Mapeador.usuario(g.getAdultoMayor()),
                Mapeador.usuario(g.getCreadoPor()), integrantes.contar(g.getId()), acceso.limiteIntegrantes(g));
    }
}
