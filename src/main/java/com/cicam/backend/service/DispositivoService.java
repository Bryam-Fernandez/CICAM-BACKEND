package com.cicam.backend.service;

import com.cicam.backend.model.Dispositivo;
import com.cicam.backend.model.Usuario;
import com.cicam.backend.repository.DispositivoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Celulares registrados para notificaciones push. Un celular pertenece a un solo usuario a la vez. */
@Service
@RequiredArgsConstructor
public class DispositivoService {

    private final DispositivoRepository dispositivos;
    private final AccesoService acceso;

    @Transactional
    public void registrar(Long usuarioId, String token) {
        Usuario u = acceso.usuario(usuarioId);
        Dispositivo d = dispositivos.findByToken(token).orElseGet(Dispositivo::new);
        d.setUsuario(u); // si otro usuario usó este celular, pasa al usuario actual
        d.setToken(token);
        dispositivos.save(d);
    }

    @Transactional
    public void baja(String token) {
        dispositivos.deleteByToken(token);
    }
}
