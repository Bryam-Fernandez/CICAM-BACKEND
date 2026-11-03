package com.cicam.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Pago del plan Premium con un link de Mercado Pago. El link solo lleva al usuario a pagar; para saber que el
 * pago es real, el servidor consulta a Mercado Pago (con el access token de TU cuenta) el número de operación
 * que el usuario informa. Un pago de otra cuenta o inexistente no aparece, así que no se puede falsificar.
 */
@Slf4j
@Service
public class MercadoPagoService {

    private static final String URL_PAGOS = "https://api.mercadopago.com/v1/payments/";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${cicam.mercadopago.link-pago:}")
    private String linkPago;

    @Value("${cicam.mercadopago.access-token:}")
    private String accessToken;

    /** Datos del pago confirmados por Mercado Pago. */
    public record PagoMp(String id, String estado, BigDecimal monto, String moneda,
                         OffsetDateTime fechaAprobado, String email) {
    }

    public String linkPago() {
        return linkPago == null ? "" : linkPago.trim();
    }

    /** El pago se puede verificar solo si hay link y access token configurados. */
    public boolean configurado() {
        return !linkPago().isEmpty() && accessToken != null && !accessToken.isBlank();
    }

    public PagoMp consultar(String operacion) {
        if (!configurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Los pagos aún no están configurados en el servidor (falta el link o el access token de Mercado Pago)");
        }
        if (operacion == null || !operacion.matches("\\d{6,20}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de operación solo debe llevar dígitos");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL_PAGOS + operacion))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No encontramos ese número de operación en Mercado Pago. Revisa que sea el de tu comprobante");
            }
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                log.error("Mercado Pago rechazó el access token del servidor (HTTP {})", resp.statusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El servidor no pudo autenticarse con Mercado Pago (revisa el access token)");
            }
            if (resp.statusCode() != 200) {
                log.warn("Mercado Pago respondió {}: {}", resp.statusCode(), recorte(resp.body()));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Mercado Pago no pudo confirmar el pago ahora (error " + resp.statusCode() + "). Intenta de nuevo");
            }
            JsonNode j = mapper.readTree(resp.body());
            BigDecimal monto = j.hasNonNull("transaction_amount")
                    ? new BigDecimal(j.get("transaction_amount").asText()) : BigDecimal.ZERO;
            return new PagoMp(
                    j.path("id").asText(operacion),
                    j.path("status").asText(""),
                    monto,
                    j.path("currency_id").asText(""),
                    fecha(j.path("date_approved").asText("")),
                    j.path("payer").path("email").asText(""));
        } catch (IOException e) {
            log.error("Error al comunicarse con Mercado Pago", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comunicar con Mercado Pago");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "La consulta del pago fue interrumpida");
        }
    }

    private OffsetDateTime fecha(String texto) {
        if (texto.isBlank()) return null;
        try {
            return OffsetDateTime.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String recorte(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
