package com.cicam.backend.repository;

import com.cicam.backend.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HistorialActividadRepository extends JpaRepository<HistorialActividad, Long> {
    @Query("select h from HistorialActividad h join fetch h.actividad a where a.adultoMayor.id = :adultoId order by h.fecha desc")
    List<HistorialActividad> porAdulto(@Param("adultoId") Long adultoId);
}
