package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "actividades")
@Getter
@Setter
@NoArgsConstructor
public class Actividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fechaLimite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoActividad estado = EstadoActividad.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adulto_mayor_id", nullable = false)
    private Usuario adultoMayor;

    /** Null = actividad sin asignar. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuidador_responsable_id")
    private Usuario cuidadorResponsable;

    /** Familiar que la creo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creada_por_id", nullable = false)
    private Usuario creadaPor;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    private LocalDateTime fechaCompletada;

    /** Borrado logico: oculta la actividad sin perder su historial. */
    @Column(nullable = false)
    private boolean eliminada = false;

    private LocalDateTime fechaEliminacion;
}
