package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "historial_actividad")
@Getter
@Setter
@NoArgsConstructor
public class HistorialActividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actividad_id", nullable = false)
    private Actividad actividad;

    /** Null cuando la actividad se acaba de crear. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoActividad estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoActividad estadoNuevo;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    /** MANUAL = lo hizo una persona; SISTEMA = vencimiento automatico. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenCambio origen;

    /** Null cuando el origen es SISTEMA. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id")
    private Usuario autor;
}
