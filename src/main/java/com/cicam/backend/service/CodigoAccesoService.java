package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.CodigoResponse;
import com.cicam.backend.model.CodigoAcceso;
import com.cicam.backend.model.EstadoCodigo;
import com.cicam.backend.model.Usuario;
import com.cicam.backend.repository.CodigoAccesoRepository;
import com.cicam.backend.security.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Código de acceso del adulto mayor:
 * - 10 caracteres aleatorios (SecureRandom), sin letras confusas, único en todo el sistema.
 * - Solo se guarda su hash SHA-256.
 * - Se puede usar sin límite de veces (p. ej. en varios celulares) y no vence, salvo que se configure
 *   una duración en cicam.codigo.horas-expiracion.
 * - Generar uno nuevo revoca el anterior y cierra las sesiones del adulto mayor.
 */
@Service
@RequiredArgsConstructor
public class CodigoAccesoService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_INTENTOS = 5;

    private final CodigoAccesoRepository codigos;
    private final SesionService sesiones;

    /** 0 = el código no vence. */
    @Value("${cicam.codigo.horas-expiracion:0}")
    private long horasExpiracion;

    @Transactional
    public CodigoResponse generar(Usuario adulto, Usuario familiar) {
        for (CodigoAcceso previo : codigos.findByAdultoMayorIdAndEstado(adulto.getId(), EstadoCodigo.ACTIVO)) {
            previo.setEstado(EstadoCodigo.REVOCADO);
        }
        sesiones.revocarTodas(adulto.getId());

        // El código no puede coincidir con el de ningún otro adulto mayor (de ningún grupo)
        String plano;
        String hash;
        int intentos = 0;
        do {
            plano = TokenUtil.aleatorio(10, ALFABETO);
            hash = TokenUtil.sha256(plano);
        } while (codigos.findByCodigoHash(hash).isPresent() && ++intentos < 10);
        if (codigos.findByCodigoHash(hash).isPresent()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar un código único");
        }

        CodigoAcceso c = new CodigoAcceso();
        c.setAdultoMayor(adulto);
        c.setGeneradoPor(familiar);
        c.setCodigoHash(hash);
        c.setFechaExpira(horasExpiracion > 0 ? LocalDateTime.now().plusHours(horasExpiracion) : null);
        codigos.save(c);
        return new CodigoResponse(plano.substring(0, 5) + "-" + plano.substring(5), c.getFechaExpira());
    }

    /** Valida el código y lo marca como usado. Los fallos sobre un código existente se cuentan. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Usuario canjear(String ingresado) {
        String normalizado = ingresado.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        CodigoAcceso c = codigos.findByCodigoHash(TokenUtil.sha256(normalizado))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código inválido"));

        LocalDateTime ahora = LocalDateTime.now();
        if (c.getBloqueadoHasta() != null && c.getBloqueadoHasta().isAfter(ahora)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Código bloqueado temporalmente");
        }
        if (c.getEstado() != EstadoCodigo.ACTIVO) {
            fallo(c);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código inválido o reemplazado por uno nuevo");
        }
        if (c.getFechaExpira() != null && c.getFechaExpira().isBefore(ahora)) {
            c.setEstado(EstadoCodigo.EXPIRADO);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "El código venció. Pide al familiar que genere uno nuevo.");
        }
        c.setFechaUso(ahora); // el código sigue activo: se puede usar sin límite
        Usuario adulto = c.getAdultoMayor();
        Hibernate.initialize(adulto); // se usa fuera de esta transacción
        return adulto;
    }

    private void fallo(CodigoAcceso c) {
        c.setIntentosFallidos(c.getIntentosFallidos() + 1);
        if (c.getIntentosFallidos() >= MAX_INTENTOS) {
            c.setBloqueadoHasta(LocalDateTime.now().plusMinutes(15));
            c.setIntentosFallidos(0);
        }
    }
}
