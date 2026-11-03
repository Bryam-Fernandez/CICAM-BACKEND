package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.*;
import com.cicam.backend.service.SaludService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaludController {

    private final SaludService service;

    // ----- Medicamentos -----
    @GetMapping("/medicamentos")
    public List<MedicamentoResponse> medicamentos(@AuthenticationPrincipal Long usuarioId, @RequestParam Long grupoId) {
        return service.listarMedicamentos(usuarioId, grupoId);
    }

    @PostMapping("/medicamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicamentoResponse crearMedicamento(@AuthenticationPrincipal Long usuarioId,
                                                @Valid @RequestBody MedicamentoRequest r) {
        return service.crearMedicamento(usuarioId, r);
    }

    @PutMapping("/medicamentos/{id}")
    public MedicamentoResponse actualizarMedicamento(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id,
                                                     @Valid @RequestBody MedicamentoRequest r) {
        return service.actualizarMedicamento(usuarioId, id, r);
    }

    @DeleteMapping("/medicamentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarMedicamento(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        service.eliminarMedicamento(usuarioId, id);
    }

    @GetMapping("/citas")
    public List<CitaResponse> citas(@AuthenticationPrincipal Long usuarioId, @RequestParam Long grupoId) {
        return service.listarCitas(usuarioId, grupoId);
    }

    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    public CitaResponse crearCita(@AuthenticationPrincipal Long usuarioId, @Valid @RequestBody CitaRequest r) {
        return service.crearCita(usuarioId, r);
    }

    @PutMapping("/citas/{id}")
    public CitaResponse actualizarCita(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id,
                                       @Valid @RequestBody CitaRequest r) {
        return service.actualizarCita(usuarioId, id, r);
    }

    @DeleteMapping("/citas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCita(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        service.eliminarCita(usuarioId, id);
    }
}
