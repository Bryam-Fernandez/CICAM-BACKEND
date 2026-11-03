package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.ChatGrupoResponse;
import com.cicam.backend.dto.Dtos.MensajeGrupoRequest;
import com.cicam.backend.dto.Dtos.MensajeGrupoResponse;
import com.cicam.backend.service.ChatGrupoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatGrupoController {

    private final ChatGrupoService service;

    @GetMapping("/grupos/{grupoId}/chat")
    public ChatGrupoResponse listar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId,
                                    @RequestParam(defaultValue = "0") Long despuesDeId,
                                    @RequestParam(defaultValue = "0") Long antesDeId) {
        return service.listar(usuarioId, grupoId, despuesDeId, antesDeId);
    }

    @PostMapping("/grupos/{grupoId}/chat")
    @ResponseStatus(HttpStatus.CREATED)
    public MensajeGrupoResponse enviar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long grupoId,
                                       @Valid @RequestBody MensajeGrupoRequest r) {
        return service.enviar(usuarioId, grupoId, r.texto());
    }
}
