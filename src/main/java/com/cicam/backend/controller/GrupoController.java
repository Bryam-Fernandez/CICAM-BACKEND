package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.*;
import com.cicam.backend.service.ActividadService;
import com.cicam.backend.service.GrupoService;
import com.cicam.backend.service.PermisoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GrupoController {

    private final GrupoService grupos;
    private final PermisoService permisos;
    private final ActividadService actividades;

    @PostMapping("/grupos")
    @ResponseStatus(HttpStatus.CREATED)
    public GrupoResponse crearGrupo(@AuthenticationPrincipal Long usuarioId, @Valid @RequestBody GrupoRequest r) {
        return grupos.crearGrupo(usuarioId, r.nombre());
    }

    @PutMapping("/grupos/{grupoId}")
    public GrupoResponse renombrar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId, @Valid @RequestBody GrupoRequest r) {
        return grupos.renombrar(usuarioId, grupoId, r.nombre());
    }

    @DeleteMapping("/grupos/{grupoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarGrupo(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId) {
        grupos.eliminar(usuarioId, grupoId);
    }

    @PostMapping("/grupos/{grupoId}/adulto")
    @ResponseStatus(HttpStatus.CREATED)
    public AdultoRegistradoResponse agregarAdulto(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId,
                                                  @Valid @RequestBody AdultoRequest r) {
        return grupos.agregarAdulto(usuarioId, grupoId, r);
    }

    @GetMapping("/grupos")
    public List<GrupoResponse> listar(@AuthenticationPrincipal Long usuarioId) {
        return grupos.listar(usuarioId);
    }

    @GetMapping("/grupos/{grupoId}/integrantes")
    public List<IntegranteResponse> integrantes(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId) {
        return grupos.listarIntegrantes(usuarioId, grupoId);
    }

    @PostMapping("/grupos/{grupoId}/integrantes")
    @ResponseStatus(HttpStatus.CREATED)
    public IntegranteResponse agregar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId,
                                      @Valid @RequestBody IntegranteRequest r) {
        return grupos.agregarIntegrante(usuarioId, grupoId, r.email());
    }

    @PostMapping("/grupos/{grupoId}/codigo")
    public CodigoResponse nuevoCodigo(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId) {
        return grupos.nuevoCodigo(usuarioId, grupoId);
    }

    @GetMapping("/grupos/{grupoId}/historial")
    public List<HistorialResponse> historial(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId,
                                             @RequestParam(required = false) Long autorId,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return actividades.historial(usuarioId, grupoId, autorId, fecha);
    }

    @GetMapping("/grupos/{grupoId}/cuidadores/{cuidadorId}/permisos")
    public List<PermisoResponse> permisos(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId,
                                          @PathVariable Long cuidadorId) {
        return permisos.listar(usuarioId, grupoId, cuidadorId);
    }

    @PostMapping("/grupos/{grupoId}/cuidadores/{cuidadorId}/permisos")
    @ResponseStatus(HttpStatus.CREATED)
    public PermisoResponse otorgar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId,
                                   @PathVariable Long cuidadorId, @Valid @RequestBody PermisoRequest r) {
        return permisos.otorgar(usuarioId, grupoId, cuidadorId, r.permiso());
    }

    @DeleteMapping("/grupos/{grupoId}/permisos/{permisoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId,
                        @PathVariable Long permisoId) {
        permisos.retirar(usuarioId, grupoId, permisoId);
    }
}
