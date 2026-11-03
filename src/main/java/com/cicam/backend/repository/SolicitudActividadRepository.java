package com.cicam.backend.repository;

import com.cicam.backend.model.EstadoSolicitud;
import com.cicam.backend.model.SolicitudActividad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudActividadRepository extends JpaRepository<SolicitudActividad, Long> {

    List<SolicitudActividad> findByActividadIdAndEstado(Long actividadId, EstadoSolicitud estado);

    Optional<SolicitudActividad> findFirstByActividadIdAndEstado(Long actividadId, EstadoSolicitud estado);

    List<SolicitudActividad> findByFamiliarIdAndEstadoOrderByFechaSolicitudDesc(Long familiarId, EstadoSolicitud estado);
}
