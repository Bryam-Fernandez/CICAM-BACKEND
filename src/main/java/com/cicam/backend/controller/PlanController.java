package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.ContratarPlanRequest;
import com.cicam.backend.dto.Dtos.PlanesResponse;
import com.cicam.backend.dto.Dtos.SuscripcionResponse;
import com.cicam.backend.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService service;

    @GetMapping("/planes")
    public PlanesResponse planes(@AuthenticationPrincipal Long usuarioId) {
        return service.planes(usuarioId);
    }

    /** Prueba gratuita de Premium (una sola vez por familiar). */
    @PostMapping("/suscripciones/prueba")
    @ResponseStatus(HttpStatus.CREATED)
    public SuscripcionResponse prueba(@AuthenticationPrincipal Long usuarioId) {
        return service.iniciarPrueba(usuarioId);
    }

    @PostMapping("/suscripciones")
    @ResponseStatus(HttpStatus.CREATED)
    public SuscripcionResponse contratar(@AuthenticationPrincipal Long usuarioId,
                                         @Valid @RequestBody ContratarPlanRequest r) {
        return service.contratar(usuarioId, r);
    }
}
