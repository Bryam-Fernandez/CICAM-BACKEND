package com.cicam.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@ConditionalOnExpression("!'${cicam.fcm.credenciales:}'.isEmpty()")
public class FirebaseConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging(@Value("${cicam.fcm.credenciales}") String ruta) throws IOException {
        try (InputStream in = new FileInputStream(ruta)) {
            FirebaseOptions opciones = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(opciones) : FirebaseApp.getInstance();
            return FirebaseMessaging.getInstance(app);
        }
    }
}
