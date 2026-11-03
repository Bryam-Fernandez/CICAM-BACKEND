package com.cicam.backend.repository;

import com.cicam.backend.model.ChatMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMensajeRepository extends JpaRepository<ChatMensaje, Long> {
    List<ChatMensaje> findBySesionIdOrderByIdAsc(Long sesionId);

    /** Los 12 mensajes más recientes (6 turnos), del más nuevo al más antiguo. */
    List<ChatMensaje> findTop12BySesionIdOrderByIdDesc(Long sesionId);

    /** Preguntas que el usuario le hizo al asistente desde un momento dado (límite diario del plan). */
    @Query("select count(m) from ChatMensaje m where m.rol = 'USER' and m.fecha >= :desde "
            + "and m.sesionId in (select s.id from ChatSesion s where s.usuarioId = :usuarioId)")
    long contarConsultasDesde(@Param("usuarioId") Long usuarioId, @Param("desde") LocalDateTime desde);
}
