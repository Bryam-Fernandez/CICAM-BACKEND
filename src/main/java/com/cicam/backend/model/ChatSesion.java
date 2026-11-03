package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Conversación del asistente de IA de un usuario sobre un grupo (se conserva para retomarla después). */
@Entity
@Table(name = "chat_sesiones")
@Getter
@Setter
@NoArgsConstructor
public class ChatSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "grupo_id", nullable = false)
    private Long grupoId;

    @Column(nullable = false, length = 80)
    private String titulo;

    @Column(nullable = false)
    private boolean fijado = false;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion = LocalDateTime.now();
}
