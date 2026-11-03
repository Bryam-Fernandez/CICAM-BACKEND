package com.cicam.backend.repository;

import com.cicam.backend.model.Dispositivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    Optional<Dispositivo> findByToken(String token);

    List<Dispositivo> findByUsuarioId(Long usuarioId);

    void deleteByToken(String token);

    void deleteByUsuarioId(Long usuarioId);
}
