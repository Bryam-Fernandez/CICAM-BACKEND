package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "grupo_integrantes")
@Getter
@Setter
@NoArgsConstructor
public class GrupoIntegrante {

    @EmbeddedId
    private GrupoIntegranteId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("grupoId")
    @JoinColumn(name = "grupo_id")
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("usuarioId")
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agregado_por_id", nullable = false)
    private Usuario agregadoPor;

    @Column(nullable = false)
    private LocalDateTime fechaIngreso = LocalDateTime.now();
}
