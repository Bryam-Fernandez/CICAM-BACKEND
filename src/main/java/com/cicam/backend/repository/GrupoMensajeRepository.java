package com.cicam.backend.repository;

import com.cicam.backend.model.GrupoMensaje;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GrupoMensajeRepository extends JpaRepository<GrupoMensaje, Long> {

    /** Mensajes nuevos (después de un id), del más antiguo al más reciente. */
    @Query("select m from GrupoMensaje m join fetch m.autor where m.grupoId = :grupoId and m.id > :despuesDeId "
            + "and m.fecha >= :desde order by m.id asc")
    List<GrupoMensaje> nuevos(@Param("grupoId") Long grupoId, @Param("despuesDeId") Long despuesDeId,
                              @Param("desde") LocalDateTime desde, Pageable pagina);

    /** Mensajes anteriores a un id, del más reciente al más antiguo (para cargar el historial por páginas). */
    @Query("select m from GrupoMensaje m join fetch m.autor where m.grupoId = :grupoId and m.id < :antesDeId "
            + "and m.fecha >= :desde order by m.id desc")
    List<GrupoMensaje> anteriores(@Param("grupoId") Long grupoId, @Param("antesDeId") Long antesDeId,
                                  @Param("desde") LocalDateTime desde, Pageable pagina);
}
