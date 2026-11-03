package com.cicam.backend.dto;

import com.cicam.backend.model.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


public final class Dtos {

    private Dtos() {
    }

    public record RegistroRequest(
            @NotBlank String nombre,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "debe tener al menos 6 caracteres") String password,
            @NotNull Rol rol,
            boolean consentimientoDatos) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record CodigoRequest(@NotBlank String codigo) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** fcmToken es opcional: si viene, se quita ese celular de las notificaciones. */
    public record LogoutRequest(@NotBlank String refreshToken, String fcmToken) {
    }

    public record DispositivoRequest(@NotBlank String token) {
    }

    public record AuthResponse(String accessToken, String refreshToken, Long usuarioId, String nombre, Rol rol) {
    }

    public record UsuarioResumen(Long id, String nombre, String email, Rol rol) {
    }

    public record GrupoRequest(@NotBlank @Size(max = 120) String nombre) {
    }

    public record AdultoRequest(
            @NotBlank String nombre,
            @NotNull @Past LocalDate fechaNacimiento,
            @NotBlank @Pattern(regexp = "[0-9]{8}") String dni,
            boolean consentimientoDatos) {
    }

    public record GrupoResponse(
            Long id, String nombre, UsuarioResumen adultoMayor, UsuarioResumen creadoPor,
            long totalIntegrantes, int limiteIntegrantes) {
    }

    /** El codigo solo se muestra en esta respuesta; luego solo queda su hash. */
    public record CodigoResponse(String codigo, LocalDateTime expira) {
    }

    public record AdultoRegistradoResponse(GrupoResponse grupo, CodigoResponse codigoAcceso) {
    }

    public record IntegranteRequest(@NotBlank @Email String email) {
    }

    public record IntegranteResponse(UsuarioResumen usuario, LocalDateTime fechaIngreso) {
    }

    // ----- Permisos del cuidador -----
    public record PermisoRequest(@NotBlank String permiso) {
    }

    public record PermisoResponse(Long id, Long cuidadorId, String permiso, LocalDateTime fechaOtorgado) {
    }

    // ----- Actividades (grupoId solo se usa al crear) -----
    public record ActividadRequest(
            Long grupoId,
            @NotBlank String descripcion,
            @NotNull LocalDateTime fechaLimite,
            Long cuidadorResponsableId) {
    }

    public record ActividadResponse(
            Long id, String descripcion, LocalDateTime fechaLimite, EstadoActividad estado,
            UsuarioResumen responsable, UsuarioResumen creadaPor,
            LocalDateTime fechaCreacion, LocalDateTime fechaCompletada,
            UsuarioResumen solicitadoA) {
    }

    public record SolicitudResponse(
            Long id, Long actividadId, String actividad, String adultoMayor, LocalDateTime fechaLimite,
            String solicitadaPor, LocalDateTime fechaSolicitud) {
    }

    public record RespuestaSolicitudRequest(boolean aceptar) {
    }

    public record HistorialResponse(
            Long id, Long actividadId, String actividad, EstadoActividad estadoAnterior,
            EstadoActividad estadoNuevo, LocalDateTime fecha, OrigenCambio origen, String autor) {
    }

    public record MensajeGrupoRequest(@NotBlank @Size(max = 1000) String texto) {
    }

    public record MensajeGrupoResponse(Long id, Long autorId, String autorNombre, String autorRol, String texto,
                                       LocalDateTime fecha, boolean propio) {
    }

    /** historialCompleto = plan Premium; diasHistorial = días visibles en el plan gratuito; hayMas = hay mensajes anteriores. */
    public record ChatGrupoResponse(List<MensajeGrupoResponse> mensajes, boolean historialCompleto,
                                    int diasHistorial, boolean hayMas) {
    }

    public record MedicamentoRequest(
            Long grupoId,
            @NotBlank String nombre,
            @NotBlank String dosis,
            @NotBlank String horarios, String foto) {
    }

    public record MedicamentoResponse(Long id, String nombre, String dosis, String horarios, String foto) {
    }

    public record CitaRequest(
            Long grupoId,
            @NotBlank String titulo,
            @NotBlank String lugar,
            @NotNull LocalDateTime fechaHora,
            EstadoCita estado, String especialidad, String medico, String indicaciones, String foto) {
    }

    public record CitaResponse(Long id, String titulo, String lugar, LocalDateTime fechaHora, EstadoCita estado, String especialidad, String medico, String indicaciones, String foto) {
    }

    public record PlanResponse(String codigo, String nombre, String descripcion, double precioMensual,
                               int limiteGrupos, int limiteIntegrantes, List<String> beneficios) {
    }

    public record PlanesResponse(List<PlanResponse> planes, String planActual, LocalDateTime activoHasta,
                                 String linkPago, boolean pagoDisponible,
                                 boolean pruebaDisponible, boolean enPrueba, int diasPrueba) {
    }

    public record ContratarPlanRequest(
            @NotBlank String plan,
            @NotBlank @Pattern(regexp = "\\d{6,20}", message = "El número de operación solo lleva dígitos") String operacion) {
    }

    public record SuscripcionResponse(Long id, String plan, EstadoSuscripcion estado, LocalDateTime fechaFin) {
    }

    public record ChatRequest(
            @NotNull Long grupoId,
            @NotBlank @Size(max = 500) String mensaje,
            Long sesionId) {
    }

    public record ChatResponse(String respuesta, Long sesionId, String titulo) {
    }

    public record ChatSesionResponse(Long id, Long grupoId, String titulo, boolean fijado,
                                     LocalDateTime fechaActualizacion) {
    }

    public record ChatMensajeResponse(Long id, String rol, String contenido, LocalDateTime fecha) {
    }

    // ----- Notificaciones -----
    public record NotificacionResponse(Long id, String mensaje, LocalDateTime fecha, boolean leida) {
    }
}
