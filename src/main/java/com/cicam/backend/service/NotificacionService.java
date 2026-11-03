package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.NotificacionResponse;
import com.cicam.backend.dto.Mapeador;
import com.cicam.backend.model.GrupoIntegrante;
import com.cicam.backend.model.Notificacion;
import com.cicam.backend.model.Rol;
import com.cicam.backend.model.Usuario;
import com.cicam.backend.repository.GrupoIntegranteRepository;
import com.cicam.backend.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Cada aviso se guarda (para verlo dentro de la app) y además se envía como notificación push
 * a los celulares del usuario, una vez confirmada la transacción.
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository repo;
    private final GrupoIntegranteRepository integrantes;
    private final PushService push;

    @Transactional
    public void notificar(Usuario destino, String titulo, String mensaje) {
        Notificacion n = new Notificacion();
        n.setUsuario(destino);
        n.setMensaje(mensaje);
        repo.save(n);
        Long usuarioId = destino.getId();
        trasCommit(() -> push.enviar(usuarioId, titulo, mensaje));
    }

    /** A todos los integrantes del grupo con ese rol, menos a quien originó el cambio (puede ser null). */
    @Transactional
    public void notificarRol(Long grupoId, Rol rol, Long excluirUsuarioId, String titulo, String mensaje) {
        for (Usuario u : integrantes.usuariosPorRol(grupoId, rol)) {
            if (!u.getId().equals(excluirUsuarioId)) {
                notificar(u, titulo, mensaje);
            }
        }
    }

    /** A todos los integrantes del grupo (familiares, cuidadores y adulto mayor), menos a quien originó el cambio. */
    @Transactional
    public void notificarIntegrantes(Long grupoId, Long excluirUsuarioId, String titulo, String mensaje) {
        for (GrupoIntegrante gi : integrantes.integrantes(grupoId)) {
            Usuario u = gi.getUsuario();
            if (!u.getId().equals(excluirUsuarioId)) {
                notificar(u, titulo, mensaje);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponse> listar(Long usuarioId) {
        return repo.findByUsuarioIdOrderByFechaDesc(usuarioId).stream().map(Mapeador::notificacion).toList();
    }

    @Transactional
    public void marcarLeida(Long usuarioId, Long id) {
        Notificacion n = repo.findById(id)
                .filter(x -> x.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));
        n.setLeida(true);
    }

    private void trasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
