package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "suscripciones")
@Getter
@Setter
@NoArgsConstructor
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanTipo plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSuscripcion estado;

    /** Id del cargo en Culqi (nunca datos de tarjeta). Sin logica de pagos por ahora. */
    private String culqiChargeId;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    /** Hasta cuándo está vigente el plan. Null = sin vencimiento. */
    private LocalDateTime fechaFin;

    /** PAGO (pagó con Mercado Pago) o PRUEBA (prueba gratuita de Premium; solo una vez por familiar). */
    @Column(nullable = false, length = 10)
    private String origen = ORIGEN_PAGO;

    public static final String ORIGEN_PAGO = "PAGO";
    public static final String ORIGEN_PRUEBA = "PRUEBA";
}
