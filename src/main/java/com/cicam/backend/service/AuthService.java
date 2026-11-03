package com.cicam.backend.service;

import com.cicam.backend.dto.Dtos.AuthResponse;
import com.cicam.backend.dto.Dtos.LoginRequest;
import com.cicam.backend.dto.Dtos.RegistroRequest;
import com.cicam.backend.model.Rol;
import com.cicam.backend.model.Usuario;
import com.cicam.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final SesionService sesiones;
    private final CodigoAccesoService codigos;
    private final LimitadorCodigo limitador;

    /** Registro de FAMILIAR o CUIDADOR. El adulto mayor no se registra: lo registra un familiar. */
    @Transactional
    public AuthResponse registrar(RegistroRequest r) {
        if (r.rol() != Rol.FAMILIAR && r.rol() != Rol.CUIDADOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo un familiar o un cuidador puede registrarse; el adulto mayor lo registra un familiar");
        }
        if (!r.consentimientoDatos()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes aceptar el tratamiento de datos personales (Ley N.º 29733)");
        }
        String email = r.email().trim().toLowerCase();
        if (usuarios.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }
        Usuario u = new Usuario();
        u.setNombre(r.nombre().trim());
        u.setEmail(email);
        u.setPassword(encoder.encode(r.password()));
        u.setRol(r.rol());
        u.setConsentimientoDatos(true);
        usuarios.save(u);
        return sesiones.iniciar(u);
    }

    @Transactional
    public AuthResponse login(LoginRequest r) {
        Usuario u = usuarios.findByEmail(r.email().trim().toLowerCase())
                .filter(x -> x.getPassword() != null && encoder.matches(r.password(), x.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
        return sesiones.iniciar(u);
    }

    /** Ingreso del adulto mayor con el código que le entregó su familiar. */
    public AuthResponse loginConCodigo(String codigo, String ip) {
        limitador.verificar(ip);
        try {
            Usuario adulto = codigos.canjear(codigo);
            limitador.exito(ip);
            return sesiones.iniciar(adulto);
        } catch (ResponseStatusException e) {
            limitador.fallo(ip);
            throw e;
        }
    }
}
