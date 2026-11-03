package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.AuthResponse;
import com.cicam.backend.model.Sesion;
import com.cicam.backend.model.Usuario;
import com.cicam.backend.repository.DispositivoRepository;
import com.cicam.backend.repository.SesionRepository;
import com.cicam.backend.security.JwtService;
import com.cicam.backend.security.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/** Sesión persistente: token de acceso corto (JWT) + token de renovación guardado como hash y revocable. */
@Service
@RequiredArgsConstructor
public class SesionService {

    private final SesionRepository sesiones;
    private final DispositivoRepository dispositivos;
    private final JwtService jwt;

    @Value("${cicam.sesion.refresh-dias:30}")
    private long refreshDias;

    @Transactional
    public AuthResponse iniciar(Usuario u) {
        String refresh = TokenUtil.aleatorio(32);
        Sesion s = new Sesion();
        s.setUsuario(u);
        s.setTokenHash(TokenUtil.sha256(refresh));
        s.setFechaExpira(LocalDateTime.now().plusDays(refreshDias));
        sesiones.save(s);
        return new AuthResponse(jwt.generar(u, s.getId()), refresh, u.getId(), u.getNombre(), u.getRol());
    }

    /** Entrega un nuevo token de acceso si la sesión sigue vigente (no revocada ni vencida). */
    @Transactional(readOnly = true)
    public AuthResponse renovar(String refresh) {
        Sesion s = sesiones.findByTokenHash(TokenUtil.sha256(refresh))
                .filter(x -> !x.isRevocada() && x.getFechaExpira().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida"));
        Usuario u = s.getUsuario();
        return new AuthResponse(jwt.generar(u, s.getId()), refresh, u.getId(), u.getNombre(), u.getRol());
    }

    /** Cierre de sesión voluntario. */
    @Transactional
    public void cerrar(String refresh) {
        sesiones.findByTokenHash(TokenUtil.sha256(refresh)).ifPresent(s -> s.setRevocada(true));
    }

    @Transactional
    public void revocarTodas(Long usuarioId) {
        sesiones.findByUsuarioIdAndRevocadaFalse(usuarioId).forEach(s -> s.setRevocada(true));
        dispositivos.deleteByUsuarioId(usuarioId); // deja de recibir notificaciones en los celulares anteriores
    }

    /** Se consulta en cada petición para respetar la revocación. */
    @Transactional(readOnly = true)
    public boolean vigente(Long sesionId) {
        return sesiones.findById(sesionId)
                .map(s -> !s.isRevocada() && s.getFechaExpira().isAfter(LocalDateTime.now()))
                .orElse(false);
    }
}
