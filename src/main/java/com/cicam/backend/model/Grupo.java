package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "grupos")
@Getter
@Setter
@NoArgsConstructor
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    /** Un grupo por adulto mayor. Es null hasta que un familiar agrega al adulto mayor. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adulto_mayor_id", unique = true)
    private Usuario adultoMayor;

    /** Familiar creador: su plan define el limite de integrantes. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
