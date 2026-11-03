package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    /** Null para el adulto mayor (entra con codigo). */
    @Column(unique = true, length = 160)
    private String email;

    /** Hash BCrypt. Null para el adulto mayor. */
    @Column(length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    private LocalDate fechaNacimiento;

    @Column(length = 8, unique = true)
    private String dni;

    @Column(nullable = false)
    private boolean consentimientoDatos;

    /** Familiar que registro al adulto mayor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();
}
