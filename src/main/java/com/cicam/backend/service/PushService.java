package com.cicam.backend.service;

import com.cicam.backend.model.Dispositivo;
import com.cicam.backend.repository.DispositivoRepository;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envía notificaciones push a los celulares del usuario (Firebase Cloud Messaging).
 * Se envían como mensajes de datos para que la app arme la notificación (y oculte el detalle en la
 * pantalla de bloqueo). Si Firebase no está configurado, no hace nada. Nunca interrumpe la operación original.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final ObjectProvider<FirebaseMessaging> fcmProvider;
    private final DispositivoRepository dispositivos;

    @Async
    public void enviar(Long usuarioId, String titulo, String mensaje) {
        FirebaseMessaging fcm = fcmProvider.getIfAvailable();
        if (fcm == null) return;
        for (Dispositivo d : dispositivos.findByUsuarioId(usuarioId)) {
            try {
                fcm.send(Message.builder()
                        .setToken(d.getToken())
                        .putData("titulo", titulo)
                        .putData("mensaje", mensaje)
                        .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                        .build());
            } catch (FirebaseMessagingException e) {
                MessagingErrorCode codigo = e.getMessagingErrorCode();
                if (codigo == MessagingErrorCode.UNREGISTERED || codigo == MessagingErrorCode.INVALID_ARGUMENT) {
                    dispositivos.delete(d); // el celular ya no existe o el token no es válido
                } else {
                    log.warn("No se pudo enviar la notificación push: {}", e.getMessage());
                }
            } catch (RuntimeException e) {
                log.warn("Error inesperado al enviar la notificación push: {}", e.getMessage());
            }
        }
    }
}
