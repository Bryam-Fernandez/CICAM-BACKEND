package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medicamentos")
@Getter
@Setter
@NoArgsConstructor
public class Medicamento {

    @Lob
    @Column(name = "foto", columnDefinition = "LONGTEXT")
    private String foto;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adulto_mayor_id", nullable = false)
    private Usuario adultoMayor;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String dosis;

    /** Ej.: 08:00, 20:00 */
    @Column(nullable = false, length = 200)
    private String horarios;
}
