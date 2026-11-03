package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.ChatGrupoResponse;
import com.cicam.backend.dto.Dtos.MensajeGrupoResponse;
import com.cicam.backend.model.Grupo;
import com.cicam.backend.model.GrupoMensaje;
import com.cicam.backend.model.Rol;
import com.cicam.backend.model.Usuario;
import com.cicam.backend.repository.GrupoIntegranteRepository;
import com.cicam.backend.repository.GrupoMensajeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chat del grupo entre familiares y cuidadores (el adulto mayor no participa).
 * Está disponible en todos los planes: así toda la coordinación del cuidado puede hacerse dentro de CICAM.
 * Diferencia por plan: el gratuito ve los mensajes de los últimos días; Premium ve todo el historial.
 * Cada mensaje nuevo llega como notificación push al resto del grupo (no se guarda en la bandeja de avisos).
 */
@Service
@RequiredArgsConstructor
public class ChatGrupoService {

    private static final int PAGINA = 60;
    private static final int MAX_NUEVOS = 200;
    private static final LocalDateTime SIN_LIMITE = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final GrupoMensajeRepository mensajes;
    private final GrupoIntegranteRepository integrantes;
    private final AccesoService acceso;
    private final PushService push;

    /**
     * Sin parámetros: los últimos mensajes. Con despuesDeId: solo los nuevos. Con antesDeId: la página anterior.
     */
    @Transactional(readOnly = true)
    public ChatGrupoResponse listar(Long usuarioId, Long grupoId, Long despuesDeId, Long antesDeId) {
        Grupo g = integranteDelChat(usuarioId, grupoId);
        boolean premium = acceso.grupoPremium(g);
        int dias = acceso.diasChat(premium);
        LocalDateTime desde = dias > 0 ? LocalDateTime.now().minusDays(dias) : SIN_LIMITE;

        List<GrupoMensaje> lista;
        boolean hayMas = false;
        if (despuesDeId != null && despuesDeId > 0) {
            lista = mensajes.nuevos(grupoId, despuesDeId, desde, PageRequest.of(0, MAX_NUEVOS));
        } else {
            long antes = (antesDeId != null && antesDeId > 0) ? antesDeId : Long.MAX_VALUE;
            lista = new ArrayList<>(mensajes.anteriores(grupoId, antes, desde, PageRequest.of(0, PAGINA)));
            hayMas = lista.size() == PAGINA;
            Collections.reverse(lista);
        }
        List<MensajeGrupoResponse> respuesta = new ArrayList<>();
        for (GrupoMensaje m : lista) {
            respuesta.add(aRespuesta(m, usuarioId));
        }
        return new ChatGrupoResponse(respuesta, premium, dias, hayMas);
    }

    @Transactional
    public MensajeGrupoResponse enviar(Long usuarioId, Long grupoId, String texto) {
        Grupo g = integranteDelChat(usuarioId, grupoId);
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un mensaje");
        }
        if (limpio.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje es muy largo (máximo 1000 caracteres)");
        }
        Usuario autor = acceso.usuario(usuarioId);
        GrupoMensaje m = new GrupoMensaje();
        m.setGrupoId(grupoId);
        m.setAutor(autor);
        m.setTexto(limpio);
        m.setFecha(LocalDateTime.now());
        m = mensajes.save(m);

        String titulo = autor.getNombre() + " · " + g.getNombre();
        String cuerpo = limpio.length() > 120 ? limpio.substring(0, 117) + "…" : limpio;
        for (Rol rol : new Rol[]{Rol.FAMILIAR, Rol.CUIDADOR}) {
            for (Usuario u : integrantes.usuariosPorRol(grupoId, rol)) {
                if (!u.getId().equals(usuarioId)) {
                    push.enviar(u.getId(), titulo, cuerpo);
                }
            }
        }
        return aRespuesta(m, usuarioId);
    }

    /** Integrante del grupo con rol de familiar o cuidador. */
    private Grupo integranteDelChat(Long usuarioId, Long grupoId) {
        Grupo g = acceso.grupoParaIntegrante(grupoId, usuarioId);
        if (acceso.usuario(usuarioId).getRol() == Rol.ADULTO_MAYOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El chat es para familiares y cuidadores");
        }
        return g;
    }

    private MensajeGrupoResponse aRespuesta(GrupoMensaje m, Long usuarioId) {
        Usuario a = m.getAutor();
        return new MensajeGrupoResponse(m.getId(), a.getId(), a.getNombre(), a.getRol().name(),
                m.getTexto(), m.getFecha(), a.getId().equals(usuarioId));
    }
}
