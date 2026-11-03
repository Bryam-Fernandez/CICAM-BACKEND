package com.cicam.backend.repository;

import com.cicam.backend.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ActividadRepository extends JpaRepository<Actividad, Long> {
    List<Actividad> findByAdultoMayorIdAndEliminadaFalseOrderByFechaLimiteAsc(Long adultoMayorId);

    List<Actividad> findByEstadoAndFechaLimiteBeforeAndEliminadaFalse(EstadoActividad estado, LocalDateTime limite);
}
