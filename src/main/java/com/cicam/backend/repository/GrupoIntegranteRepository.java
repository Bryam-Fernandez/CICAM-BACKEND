package com.cicam.backend.repository;

import com.cicam.backend.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GrupoIntegranteRepository extends JpaRepository<GrupoIntegrante, GrupoIntegranteId> {
    @Query("select gi.grupo from GrupoIntegrante gi where gi.id.usuarioId = :usuarioId")
    List<Grupo> gruposDe(@Param("usuarioId") Long usuarioId);

    @Query("select gi from GrupoIntegrante gi join fetch gi.usuario where gi.id.grupoId = :grupoId")
    List<GrupoIntegrante> integrantes(@Param("grupoId") Long grupoId);

    @Query("select count(gi) from GrupoIntegrante gi where gi.id.grupoId = :grupoId")
    long contar(@Param("grupoId") Long grupoId);

    @Query("select count(gi) from GrupoIntegrante gi where gi.id.grupoId = :grupoId and gi.id.usuarioId = :usuarioId")
    long contarIntegrante(@Param("grupoId") Long grupoId, @Param("usuarioId") Long usuarioId);

    default boolean esIntegrante(Long grupoId, Long usuarioId) {
        return contarIntegrante(grupoId, usuarioId) > 0;
    }

    @Query("select gi.usuario from GrupoIntegrante gi where gi.id.grupoId = :grupoId and gi.usuario.rol = :rol")
    List<Usuario> usuariosPorRol(@Param("grupoId") Long grupoId, @Param("rol") Rol rol);
}
