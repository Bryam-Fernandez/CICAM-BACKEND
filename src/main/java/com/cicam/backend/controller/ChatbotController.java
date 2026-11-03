package com.cicam.backend.controller;

import com.cicam.backend.dto.Dtos.ChatMensajeResponse;
import com.cicam.backend.dto.Dtos.ChatRequest;
import com.cicam.backend.dto.Dtos.ChatResponse;
import com.cicam.backend.dto.Dtos.ChatSesionResponse;
import com.cicam.backend.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService service;

    @PostMapping
    public ChatResponse consultar(@AuthenticationPrincipal Long usuarioId, @Valid @RequestBody ChatRequest r) {
        return service.responder(usuarioId, r);
    }

    @GetMapping("/sesiones")
    public List<ChatSesionResponse> sesiones(@AuthenticationPrincipal Long usuarioId, @RequestParam Long grupoId) {
        return service.listar(usuarioId, grupoId);
    }

    @GetMapping("/sesiones/{id}/mensajes")
    public List<ChatMensajeResponse> mensajes(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return service.abrir(usuarioId, id);
    }

    @PostMapping("/sesiones/{id}/fijar")
    public ChatSesionResponse fijar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return service.fijar(usuarioId, id);
    }

    @DeleteMapping("/sesiones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        service.eliminar(usuarioId, id);
    }
}
