package com.cicam.backend.repository;

import com.cicam.backend.model.ChatSesion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSesionRepository extends JpaRepository<ChatSesion, Long> {
    List<ChatSesion> findByUsuarioIdAndGrupoIdOrderByFijadoDescFechaActualizacionDesc(Long usuarioId, Long grupoId);
}
