package com.cicam.backend.repository;

import com.cicam.backend.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    @Query("select count(s) from Suscripcion s where s.usuario.id = :usuarioId and s.plan = :plan "
            + "and s.estado = :estado and (s.fechaFin is null or s.fechaFin > :ahora)")
    long contarVigentes(@Param("usuarioId") Long usuarioId, @Param("plan") PlanTipo plan,
                        @Param("estado") EstadoSuscripcion estado, @Param("ahora") LocalDateTime ahora);

    /** Suscripción vigente más reciente (la que vence más tarde). */
    @Query("select s from Suscripcion s where s.usuario.id = :usuarioId and s.plan = :plan "
            + "and s.estado = :estado order by s.fechaFin desc")
    List<Suscripcion> porUsuarioPlanEstado(@Param("usuarioId") Long usuarioId, @Param("plan") PlanTipo plan,
                                           @Param("estado") EstadoSuscripcion estado);

    /** ¿El familiar ya usó alguna vez su prueba gratuita? */
    boolean existsByUsuarioIdAndOrigen(Long usuarioId, String origen);

    /** Pruebas gratuitas activas que vencen entre dos momentos (para avisar antes de que terminen). */
    @Query("select s from Suscripcion s join fetch s.usuario where s.origen = 'PRUEBA' and s.estado = :estado "
            + "and s.fechaFin > :desde and s.fechaFin <= :hasta")
    List<Suscripcion> pruebasPorVencer(@Param("estado") EstadoSuscripcion estado,
                                       @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
