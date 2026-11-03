package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Registro de recordatorios ya enviados, para no repetirlos (citas 24 h y 2 h antes; medicamentos). */
@Entity
@Table(name = "recordatorios_enviados")
@Getter
@Setter
@NoArgsConstructor
public class RecordatorioEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CITA_24H, CITA_2H o MEDICAMENTO. */
    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "referencia_id", nullable = false)
    private Long referenciaId;

    /** Cita: su fecha y hora. Medicamento: la fecha y hora programada de la toma. */
    @Column(nullable = false)
    private LocalDateTime ocurrencia;

    @Column(nullable = false)
    private LocalDateTime fechaEnvio = LocalDateTime.now();
}
