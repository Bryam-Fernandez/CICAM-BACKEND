package com.cicam.backend.repository;

import com.cicam.backend.model.RecordatorioEnviado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface RecordatorioEnviadoRepository extends JpaRepository<RecordatorioEnviado, Long> {

    boolean existsByTipoAndReferenciaIdAndOcurrencia(String tipo, Long referenciaId, LocalDateTime ocurrencia);
}
