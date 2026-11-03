package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.RespuestaSolicitudRequest;
import com.cicam.backend.dto.Dtos.SolicitudResponse;
import com.cicam.backend.service.ActividadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/solicitudes")
@RequiredArgsConstructor
public class SolicitudController {

    private final ActividadService service;

    @GetMapping
    public List<SolicitudResponse> pendientes(@AuthenticationPrincipal Long usuarioId) {
        return service.solicitudesPendientes(usuarioId);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void responder(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id,
                          @RequestBody RespuestaSolicitudRequest r) {
        service.responderSolicitud(usuarioId, id, r.aceptar());
    }
}
