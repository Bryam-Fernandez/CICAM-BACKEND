package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "codigos_acceso")
@Getter
@Setter
@NoArgsConstructor
public class CodigoAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adulto_mayor_id", nullable = false)
    private Usuario adultoMayor;

    /** SHA-256 (hex) del codigo; el codigo nunca se guarda. */
    @Column(nullable = false, unique = true, length = 64)
    private String codigoHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generado_por_id", nullable = false)
    private Usuario generadoPor;

    @Column(nullable = false)
    private LocalDateTime fechaGenerado = LocalDateTime.now();

    /** Null = el código no vence. */
    private LocalDateTime fechaExpira;

    /** Último uso del código (el código se puede usar sin límite). */
    private LocalDateTime fechaUso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCodigo estado = EstadoCodigo.ACTIVO;

    @Column(nullable = false)
    private int intentosFallidos = 0;

    private LocalDateTime bloqueadoHasta;
}
