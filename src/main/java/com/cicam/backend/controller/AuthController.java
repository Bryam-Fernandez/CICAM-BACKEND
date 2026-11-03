package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.*;
import com.cicam.backend.service.AuthService;
import com.cicam.backend.service.DispositivoService;
import com.cicam.backend.service.SesionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;
    private final SesionService sesiones;
    private final DispositivoService dispositivos;

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registro(@Valid @RequestBody RegistroRequest r) {
        return service.registrar(r);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) {
        return service.login(r);
    }

    @PostMapping("/codigo")
    public AuthResponse codigo(@Valid @RequestBody CodigoRequest r, HttpServletRequest request) {
        return service.loginConCodigo(r.codigo(), request.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest r) {
        return sesiones.renovar(r.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest r) {
        sesiones.cerrar(r.refreshToken());
        if (r.fcmToken() != null && !r.fcmToken().isBlank()) {
            dispositivos.baja(r.fcmToken());
        }
    }
}
