package com.cicam.backend.security;

import com.cicam.backend.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** Token de acceso JWT de vida corta. Incluye el id de sesión para poder revocarlo. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessMs;

    public JwtService(@Value("${cicam.jwt.secret}") String secret,
                      @Value("${cicam.jwt.access-minutes:60}") long accessMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMs = accessMinutes * 60_000L;
    }

    public String generar(Usuario u, Long sesionId) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(String.valueOf(u.getId()))
                .claim("rol", u.getRol().name())
                .claim("sid", sesionId)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + accessMs))
                .signWith(key)
                .compact();
    }

    /** Lanza JwtException si el token es inválido o expiró. */
    public Claims leer(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
