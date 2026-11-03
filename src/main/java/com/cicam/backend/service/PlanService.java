package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.*;
import com.cicam.backend.model.*;
import com.cicam.backend.repository.PagoRepository;
import com.cicam.backend.repository.SuscripcionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Planes (GRATUITO y PREMIUM), prueba gratuita de Premium y activación tras un pago REAL en Mercado Pago.
 * Solo un FAMILIAR puede comprarlo o probarlo. El plan se activa únicamente si Mercado Pago confirma el pago
 * (aprobado, en soles, por un monto suficiente, reciente y no usado antes).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private static final int DIAS_PLAN = 30;
    /** Un pago solo sirve para activar el plan si se aprobó dentro de este plazo. */
    private static final Duration ANTIGUEDAD_MAXIMA = Duration.ofDays(3);

    private final SuscripcionRepository suscripciones;
    private final PagoRepository pagos;
    private final AccesoService acceso;
    private final MercadoPagoService mercadoPago;
    private final TransactionTemplate tx;

    /** Usuarios con una verificación en curso: evita procesar dos veces el mismo envío. */
    private final Set<Long> enCurso = ConcurrentHashMap.newKeySet();

    @Value("${cicam.plan.premium.precio:19.90}")
    private BigDecimal precioPremium;

    /** Días de la prueba gratuita de Premium (una sola vez por familiar). 0 la desactiva. */
    @Value("${cicam.plan.premium.dias-prueba:7}")
    private int diasPrueba;

    @Value("${cicam.plan.max-grupos-gratuito:1}")
    private int maxGruposGratuito;

    @Value("${cicam.plan.max-grupos-premium:5}")
    private int maxGruposPremium;

    @Transactional(readOnly = true)
    public PlanesResponse planes(Long usuarioId) {
        int diasHistorial = acceso.diasHistorial(false);
        List<String> beneficiosGratuito = List.of(
                maxGruposGratuito + (maxGruposGratuito == 1 ? " grupo (un adulto mayor)" : " grupos"),
                "Hasta " + AccesoService.LIMITE_GRATUITO + " integrantes por grupo",
                "Actividades, citas y medicamentos con recordatorios",
                "Asistente de IA: " + acceso.consultasIaPorDiaGratuito() + " consultas al día",
                "Historial de los últimos " + diasHistorial + " días",
                "Aviso de cada cita 24 h y 2 h antes",
                "Chat del grupo (mensajes de los últimos " + acceso.diasChat(false) + " días)");
        List<String> beneficiosPremium = List.of(
                "Hasta " + maxGruposPremium + " grupos (varios adultos mayores)",
                "Hasta " + AccesoService.LIMITE_PREMIUM + " integrantes por grupo",
                "Asistente de IA: " + acceso.consultasIaPorDiaPremium() + " consultas al día",
                "Historial completo, sin límite de días",
                "Chat del grupo con todo el historial",
                "Aviso de cada cita también 7 días antes",
                "Resumen semanal del cuidado cada lunes");
        List<PlanResponse> lista = List.of(
                new PlanResponse(PlanTipo.GRATUITO.name(), "Gratuito",
                        "Lo esencial para cuidar a un adulto mayor", 0.0,
                        maxGruposGratuito, AccesoService.LIMITE_GRATUITO, beneficiosGratuito),
                new PlanResponse(PlanTipo.PREMIUM.name(), "Premium",
                        "Para familias que cuidan a más de un adulto mayor", precioPremium.doubleValue(),
                        maxGruposPremium, AccesoService.LIMITE_PREMIUM, beneficiosPremium));
        Suscripcion vigente = premiumVigente(usuarioId);
        boolean enPrueba = vigente != null && Suscripcion.ORIGEN_PRUEBA.equals(vigente.getOrigen());
        boolean pruebaDisponible = diasPrueba > 0 && vigente == null
                && acceso.usuario(usuarioId).getRol() == Rol.FAMILIAR
                && !suscripciones.existsByUsuarioIdAndOrigen(usuarioId, Suscripcion.ORIGEN_PRUEBA);
        return new PlanesResponse(lista,
                vigente != null ? PlanTipo.PREMIUM.name() : PlanTipo.GRATUITO.name(),
                vigente != null ? vigente.getFechaFin() : null,
                mercadoPago.linkPago(),
                mercadoPago.configurado(),
                pruebaDisponible, enPrueba, diasPrueba);
    }

    /** Prueba gratuita de Premium: una sola vez por familiar, sin pago y sin tarjeta. */
    public SuscripcionResponse iniciarPrueba(Long usuarioId) {
        Usuario u = acceso.usuario(usuarioId);
        if (u.getRol() != Rol.FAMILIAR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un familiar puede probar el plan PREMIUM");
        }
        if (diasPrueba <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La prueba gratuita no está disponible");
        }
        if (!enCurso.add(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya estamos procesando una solicitud tuya. Espera un momento");
        }
        try {
            Suscripcion actual = premiumVigente(usuarioId);
            if (actual != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya tienes el plan PREMIUM activo hasta el " + Textos.fecha(actual.getFechaFin()));
            }
            if (suscripciones.existsByUsuarioIdAndOrigen(usuarioId, Suscripcion.ORIGEN_PRUEBA)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya usaste tu prueba gratuita de Premium");
            }
            return tx.execute(st -> {
                Suscripcion s = new Suscripcion();
                s.setUsuario(u);
                s.setPlan(PlanTipo.PREMIUM);
                s.setEstado(EstadoSuscripcion.ACTIVA);
                s.setOrigen(Suscripcion.ORIGEN_PRUEBA);
                s.setFechaFin(LocalDateTime.now().plusDays(diasPrueba));
                s = suscripciones.save(s);
                return new SuscripcionResponse(s.getId(), s.getPlan().name(), s.getEstado(), s.getFechaFin());
            });
        } finally {
            enCurso.remove(usuarioId);
        }
    }

    /** Verifica en Mercado Pago el número de operación y, si el pago es válido, activa PREMIUM por 30 días. */
    public SuscripcionResponse contratar(Long usuarioId, ContratarPlanRequest r) {
        Usuario u = acceso.usuario(usuarioId);
        if (u.getRol() != Rol.FAMILIAR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un familiar puede comprar el plan PREMIUM");
        }
        if (!PlanTipo.PREMIUM.name().equalsIgnoreCase(r.plan().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El plan gratuito no necesita pago");
        }
        if (!mercadoPago.configurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Los pagos aún no están configurados en el servidor (falta el link o el access token de Mercado Pago)");
        }
        if (precioPremium.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio del plan PREMIUM aún no está configurado");
        }
        String operacion = r.operacion().trim();
        if (!enCurso.add(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya estamos verificando un pago tuyo. Espera un momento");
        }
        try {
            Suscripcion actual = premiumVigente(usuarioId);
            // Con la prueba gratuita activa se puede pagar para continuar; con un Premium pagado, no.
            if (actual != null && !Suscripcion.ORIGEN_PRUEBA.equals(actual.getOrigen())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya tienes el plan PREMIUM activo hasta el " + Textos.fecha(actual.getFechaFin()));
            }
            if (pagos.existsByReferencia(operacion)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese pago ya fue usado para activar un plan");
            }
            MercadoPagoService.PagoMp pago = mercadoPago.consultar(operacion);
            validar(pago);
            try {
                return tx.execute(st -> registrar(u, operacion, pago, actual));
            } catch (DataIntegrityViolationException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese pago ya fue usado para activar un plan");
            } catch (RuntimeException e) {
                log.error("Pago {} de Mercado Pago aprobado pero falló al activar Premium del usuario {}",
                        operacion, usuarioId, e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Tu pago es válido pero no se pudo activar el plan. Guarda el número de operación " + operacion
                                + " y vuelve a intentarlo");
            }
        } finally {
            enCurso.remove(usuarioId);
        }
    }

    private void validar(MercadoPagoService.PagoMp p) {
        switch (p.estado()) {
            case "approved" -> {
            }
            case "pending", "in_process", "in_mediation", "authorized" -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Tu pago todavía está en proceso en Mercado Pago. Espera unos minutos y vuelve a confirmarlo");
            default -> throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "El pago no fue aprobado (estado: " + p.estado() + ")");
        }
        if (!"PEN".equalsIgnoreCase(p.moneda())) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "El pago no está en soles (PEN)");
        }
        if (p.monto().compareTo(precioPremium) < 0) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "El monto pagado (S/ " + p.monto().setScale(2, RoundingMode.HALF_UP)
                            + ") es menor al precio del plan (S/ " + precioPremium.setScale(2, RoundingMode.HALF_UP) + ")");
        }
        OffsetDateTime aprobado = p.fechaAprobado();
        if (aprobado == null || aprobado.isBefore(OffsetDateTime.now().minus(ANTIGUEDAD_MAXIMA))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ese pago es demasiado antiguo para activar el plan. Realiza un pago nuevo");
        }
    }

    private SuscripcionResponse registrar(Usuario u, String operacion, MercadoPagoService.PagoMp pago,
                                           Suscripcion prueba) {
        if (prueba != null) { // el pago reemplaza a la prueba gratuita
            suscripciones.findById(prueba.getId()).ifPresent(p -> p.setEstado(EstadoSuscripcion.CANCELADA));
        }
        Suscripcion s = new Suscripcion();
        s.setUsuario(u);
        s.setPlan(PlanTipo.PREMIUM);
        s.setEstado(EstadoSuscripcion.ACTIVA);
        s.setOrigen(Suscripcion.ORIGEN_PAGO);
        s.setCulqiChargeId(operacion); // columna heredada: guarda la referencia del pago (operación de Mercado Pago)
        s.setFechaFin(LocalDateTime.now().plusDays(DIAS_PLAN));
        s = suscripciones.save(s);

        Pago p = new Pago();
        p.setUsuarioId(u.getId());
        p.setSuscripcionId(s.getId());
        p.setPlan(PlanTipo.PREMIUM.name());
        p.setMonto(pago.monto().setScale(2, RoundingMode.HALF_UP));
        p.setMoneda("PEN");
        p.setEmail(pago.email() == null || pago.email().isBlank() ? null : pago.email());
        p.setProveedor("MERCADOPAGO");
        p.setReferencia(operacion);
        pagos.save(p);
        return new SuscripcionResponse(s.getId(), s.getPlan().name(), s.getEstado(), s.getFechaFin());
    }

    private Suscripcion premiumVigente(Long usuarioId) {
        LocalDateTime ahora = LocalDateTime.now();
        return suscripciones.porUsuarioPlanEstado(usuarioId, PlanTipo.PREMIUM, EstadoSuscripcion.ACTIVA).stream()
                .filter(s -> s.getFechaFin() == null || s.getFechaFin().isAfter(ahora))
                .findFirst().orElse(null);
    }
}
