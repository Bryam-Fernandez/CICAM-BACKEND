package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.ActividadRequest;
import com.cicam.backend.dto.Dtos.ActividadResponse;
import com.cicam.backend.model.EstadoActividad;
import com.cicam.backend.service.ActividadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/actividades")
@RequiredArgsConstructor
public class ActividadController {

    private final ActividadService service;

    @GetMapping
    public List<ActividadResponse> listar(@AuthenticationPrincipal Long usuarioId,
                                          @RequestParam Long grupoId,
                                          @RequestParam(required = false) EstadoActividad estado,
                                          @RequestParam(required = false) Long responsableId,
                                          @RequestParam(required = false) Boolean sinAsignar,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.listar(usuarioId, grupoId, estado, responsableId, Boolean.TRUE.equals(sinAsignar), fecha);
    }

    @GetMapping("/{id}")
    public ActividadResponse obtener(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return service.obtener(usuarioId, id);
    }

    /** Solo familiar. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActividadResponse crear(@AuthenticationPrincipal Long usuarioId, @Valid @RequestBody ActividadRequest r) {
        return service.crear(usuarioId, r);
    }

    @PutMapping("/{id}")
    public ActividadResponse actualizar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id,
                                        @Valid @RequestBody ActividadRequest r) {
        return service.actualizar(usuarioId, id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        service.eliminar(usuarioId, id);
    }

    /** Solo familiar: toma por sí mismo una actividad sin responsable. */
    @PutMapping("/{id}/tomar")
    public ActividadResponse tomar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return service.tomar(usuarioId, id);
    }

    /** Familiar o cuidador: confirma que la actividad fue realizada (ver reglas en ActividadService). */
    @PutMapping("/{id}/completar")
    public ActividadResponse completar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return service.completar(usuarioId, id);
    }
}
