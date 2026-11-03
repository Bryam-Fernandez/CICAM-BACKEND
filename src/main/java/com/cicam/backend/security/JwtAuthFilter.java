package com.cicam.backend.security;

import com.cicam.backend.service.SesionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Autentica con el token de acceso y verifica que su sesión no haya sido revocada. */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SesionService sesionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.leer(header.substring(7));
                Object sid = claims.get("sid"); // jjwt puede devolverlo como Integer o Long
                Long sesionId = sid instanceof Number ? ((Number) sid).longValue() : null;
                if (sesionId != null && sesionService.vigente(sesionId)) {
                    Long usuarioId = Long.valueOf(claims.getSubject());
                    var auth = new UsernamePasswordAuthenticationToken(
                            usuarioId, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("rol", String.class))));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Token inválido o vencido: se continúa sin autenticar y la API responde 401.
            }
        }
        chain.doFilter(request, response);
    }
}
