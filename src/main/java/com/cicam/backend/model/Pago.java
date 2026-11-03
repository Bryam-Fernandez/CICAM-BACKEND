package com.cicam.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pago aprobado (Mercado Pago). Guarda solo la referencia de la operación y el monto; nunca datos de tarjeta. */
@Entity
@Table(name = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "suscripcion_id")
    private Long suscripcionId;

    @Column(nullable = false, length = 20)
    private String plan;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    private String moneda = "PEN";

    @Column(length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String proveedor = "MERCADOPAGO";

    /** Número de operación del proveedor de pagos (único: un pago activa un solo plan). */
    @Column(nullable = false, length = 100)
    private String referencia;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
