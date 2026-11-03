package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.NotificacionResponse;
import com.cicam.backend.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService service;

    @GetMapping
    public List<NotificacionResponse> listar(@AuthenticationPrincipal Long usuarioId) {
        return service.listar(usuarioId);
    }

    @PutMapping("/{id}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLeida(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        service.marcarLeida(usuarioId, id);
    }
}
