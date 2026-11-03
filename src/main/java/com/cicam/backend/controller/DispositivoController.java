package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.DispositivoRequest;
import com.cicam.backend.service.DispositivoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dispositivos")
@RequiredArgsConstructor
public class DispositivoController {

    private final DispositivoService service;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@AuthenticationPrincipal Long usuarioId, @Valid @RequestBody DispositivoRequest r) {
        service.registrar(usuarioId, r.token());
    }
}
