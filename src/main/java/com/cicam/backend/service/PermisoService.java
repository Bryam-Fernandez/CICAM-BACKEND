package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.PermisoResponse;
import com.cicam.backend.model.*;
import com.cicam.backend.repository.CuidadorPermisoRepository;
import com.cicam.backend.repository.GrupoIntegranteRepository;
import com.cicam.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Permisos que un FAMILIAR puede otorgar o retirar a un CUIDADOR.
 * Solo un familiar del grupo puede hacerlo (el cuidador y el adulto mayor no).
 * IMPORTANTE: todavía no hay permisos adicionales definidos, así que el catálogo está vacío
 * y por ahora no se puede otorgar ninguno. Al aprobarse un permiso, se agrega a PERMISOS_DEFINIDOS.
 */
@Service
@RequiredArgsConstructor
public class PermisoService {

    public static final Set<String> PERMISOS_DEFINIDOS = Set.of("GESTIONAR_MEDICAMENTOS", "GESTIONAR_CITAS");

    private final CuidadorPermisoRepository permisos;
    private final UsuarioRepository usuarios;
    private final GrupoIntegranteRepository integrantes;
    private final AccesoService acceso;

    private Grupo exigirCreador(Long grupoId, Long usuarioId) {
        Grupo g = acceso.grupoParaIntegrante(grupoId, usuarioId);
        if (!g.getCreadoPor().getId().equals(usuarioId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el creador administra permisos");
        return g;
    }

    @Transactional
    public PermisoResponse otorgar(Long familiarId, Long grupoId, Long cuidadorId, String permiso) {
        Grupo g = exigirCreador(grupoId, familiarId);
        Usuario cuidador = usuarios.findById(cuidadorId)
                .filter(u -> u.getRol() == Rol.CUIDADOR && integrantes.esIntegrante(grupoId, u.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El permiso solo puede otorgarse a un cuidador del grupo"));
        if (!PERMISOS_DEFINIDOS.contains(permiso)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No hay permisos adicionales definidos todavía");
        }
        var activos = permisos.findByGrupoIdAndCuidadorIdAndFechaRetiroIsNull(grupoId, cuidadorId);
        for (CuidadorPermiso existente : activos) if (existente.getPermiso().equals(permiso)) return respuesta(existente);
        CuidadorPermiso p = new CuidadorPermiso();
        p.setGrupo(g);
        p.setCuidador(cuidador);
        p.setPermiso(permiso);
        p.setOtorgadoPor(acceso.usuario(familiarId));
        permisos.save(p);
        return respuesta(p);
    }

    @Transactional
    public void retirar(Long familiarId, Long grupoId, Long permisoId) {
        exigirCreador(grupoId, familiarId);
        CuidadorPermiso p = permisos.findById(permisoId)
                .filter(x -> x.getGrupo().getId().equals(grupoId) && x.getFechaRetiro() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permiso no encontrado"));
        p.setFechaRetiro(LocalDateTime.now());
        p.setRetiradoPor(acceso.usuario(familiarId));
    }

    @Transactional(readOnly = true)
    public List<PermisoResponse> listar(Long familiarId, Long grupoId, Long cuidadorId) {
        if (familiarId.equals(cuidadorId)) {
            acceso.grupoParaCuidador(grupoId, familiarId);
        } else {
            exigirCreador(grupoId, familiarId);
        }
        return permisos.findByGrupoIdAndCuidadorIdAndFechaRetiroIsNull(grupoId, cuidadorId)
                .stream().map(this::respuesta).toList();
    }

    private PermisoResponse respuesta(CuidadorPermiso p) {
        return new PermisoResponse(p.getId(), p.getCuidador().getId(), p.getPermiso(), p.getFechaOtorgado());
    }
}
