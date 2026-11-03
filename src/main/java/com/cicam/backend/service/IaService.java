package com.cicam.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente de la API de IA. Proveedores (se usan en este orden, solo los que estén configurados):
 * GEMINI (Google AI Studio, tiene nivel gratuito), CLAUDE (Anthropic) y OLLAMA (modelo propio, como en NutriMama).
 * NO hay respuestas predeterminadas: si ningún proveedor responde, se lanza un error con la causa real
 * y la app se lo muestra al usuario. Las claves viven solo en el servidor (variables de entorno).
 */
@Slf4j
@Service
public class IaService {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    // --- Gemini (https://aistudio.google.com/apikey) ---
    @Value("${cicam.ia.gemini.clave:}")
    private String claveGemini;

    @Value("${cicam.ia.gemini.modelo:gemini-3.8-flash}")
    private String modeloGemini;

    /** Modelos ESTABLES de Gemini que se prueban, en orden, si el principal está saturado (503) o sin cuota. */
    @Value("${cicam.ia.gemini.modelos-respaldo:gemini-3.6-flash,gemini-3.5-flash-lite}")
    private String modelosGeminiRespaldo;

    // --- Claude (https://console.anthropic.com) ---
    @Value("${cicam.ia.clave:}")
    private String claveClaude;

    @Value("${cicam.ia.modelo:claude-haiku-5-5}")
    private String modeloClaude;

    // --- Ollama (servidor propio con API compatible con OpenAI, p. ej. http://localhost:11434) ---
    @Value("${cicam.ia.ollama.url:}")
    private String urlOllama;

    @Value("${cicam.ia.ollama.modelo:}")
    private String modeloOllama;

    /** Falla de un proveedor concreto, con la causa legible. */
    private static class FalloProveedor extends Exception {
        /** true si conviene reintentar (servidor saturado: 500/503/504). */
        final boolean transitorio;

        FalloProveedor(String mensaje) {
            this(mensaje, false);
        }

        FalloProveedor(String mensaje, boolean transitorio) {
            super(mensaje);
            this.transitorio = transitorio;
        }
    }

    public boolean disponible() {
        return hay(claveGemini) || hay(claveClaude) || (hay(urlOllama) && hay(modeloOllama));
    }

    /** Lanza 503 con un mensaje claro si no hay ningún proveedor de IA configurado. */
    public void exigirDisponible() {
        if (!disponible()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El asistente de IA no está configurado en el servidor (falta GEMINI_API_KEY, ANTHROPIC_API_KEY u OLLAMA_URL)");
        }
    }

    private boolean hay(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * mensajes: lista de {role: user|assistant, content: texto}; debe empezar y terminar con "user".
     * Devuelve el texto generado por la IA o lanza ResponseStatusException (503 si no hay IA configurada,
     * 502 si los proveedores configurados fallaron).
     */
    public String responder(String sistema, List<Map<String, String>> mensajes) {
        exigirDisponible();
        List<String> fallos = new ArrayList<>();
        if (hay(claveGemini)) {
            List<String> modelos = new ArrayList<>();
            modelos.add(modeloGemini.trim());
            if (hay(modelosGeminiRespaldo)) {
                for (String m : modelosGeminiRespaldo.split(",")) {
                    String limpio = m.trim();
                    if (!limpio.isEmpty() && !modelos.contains(limpio)) modelos.add(limpio);
                }
            }
            for (String modelo : modelos) {
                try {
                    return geminiConReintento(sistema, mensajes, modelo);
                } catch (FalloProveedor e) {
                    fallos.add("Gemini " + modelo + ": " + e.getMessage());
                }
            }
        }
        if (hay(claveClaude)) {
            try {
                return conClaude(sistema, mensajes);
            } catch (FalloProveedor e) {
                fallos.add("Claude: " + e.getMessage());
            }
        }
        if (hay(urlOllama) && hay(modeloOllama)) {
            try {
                return conOllama(sistema, mensajes);
            } catch (FalloProveedor e) {
                fallos.add("Ollama: " + e.getMessage());
            }
        }
        log.warn("Ningún proveedor de IA pudo responder: {}", fallos);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "La IA no pudo responder en este momento. " + String.join(" | ", fallos));
    }

    // ---------------------------------------------------------------- Gemini

    /** Reintenta una vez si Google responde que está saturado (los 503 suelen ser de pocos segundos). */
    private String geminiConReintento(String sistema, List<Map<String, String>> mensajes, String modelo)
            throws FalloProveedor {
        try {
            return conGemini(sistema, mensajes, modelo, true);
        } catch (FalloProveedor e) {
            if (!e.transitorio) throw e;
            log.info("Gemini {} saturado, reintentando en 2 s", modelo);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new FalloProveedor("la consulta fue interrumpida");
            }
            return conGemini(sistema, mensajes, modelo, true);
        }
    }

    /**
     * Gemini 3.x piensa por defecto (más lento): se pide nivel "low", que todos los modelos usados aquí aceptan.
     * Si Google rechaza ese parámetro (400 que menciona "thinking"), se repite la consulta sin él.
     */
    private String conGemini(String sistema, List<Map<String, String>> mensajes, String modelo, boolean ajustarPensamiento)
            throws FalloProveedor {
        try {
            List<Object> contenidos = new ArrayList<>();
            for (Map<String, String> m : mensajes) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("role", "assistant".equals(m.get("role")) ? "model" : "user");
                c.put("parts", List.of(Map.of("text", m.get("content"))));
                contenidos.add(c);
            }
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("maxOutputTokens", 2000);
            config.put("temperature", 0.3);
            if (ajustarPensamiento && modelo.startsWith("gemini-3")) {
                config.put("thinkingConfig", Map.of("thinkingLevel", "low")); // respuestas más rápidas
            }
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("system_instruction", Map.of("parts", List.of(Map.of("text", sistema))));
            cuerpo.put("contents", contenidos);
            cuerpo.put("generationConfig", config);

            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/" + modelo + ":generateContent"))
                    .timeout(Duration.ofSeconds(45))
                    .header("x-goog-api-key", claveGemini)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Gemini {} respondió {}: {}", modelo, resp.statusCode(), recorte(resp.body()));
                int c = resp.statusCode();
                if (c == 400 && ajustarPensamiento && resp.body().toLowerCase().contains("thinking")) {
                    log.info("Gemini {} no acepta thinkingLevel; se repite sin ese parámetro", modelo);
                    return conGemini(sistema, mensajes, modelo, false);
                }
                throw new FalloProveedor(causaHttp(c, resp.body()), c == 500 || c == 503 || c == 504);
            }
            JsonNode raiz = mapper.readTree(resp.body());
            List<String> textos = new ArrayList<>();
            for (JsonNode parte : raiz.path("candidates").path(0).path("content").path("parts")) {
                if (parte.hasNonNull("text")) textos.add(parte.get("text").asText());
            }
            String texto = String.join("", textos).trim();
            if (texto.isEmpty()) {
                log.warn("Gemini no devolvió texto: {}", recorte(resp.body()));
                String motivo = raiz.path("promptFeedback").path("blockReason").asText("");
                throw new FalloProveedor(motivo.isEmpty() ? "no devolvió texto" : "la consulta fue bloqueada (" + motivo + ")");
            }
            return texto;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalloProveedor("la consulta fue interrumpida");
        } catch (FalloProveedor e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo consultar Gemini: {}", e.toString());
            throw new FalloProveedor("no se pudo conectar (" + e.getClass().getSimpleName() + ")");
        }
    }

    // ---------------------------------------------------------------- Claude

    private String conClaude(String sistema, List<Map<String, String>> mensajes) throws FalloProveedor {
        try {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("model", modeloClaude);
            cuerpo.put("max_tokens", 1000);
            cuerpo.put("system", sistema);
            cuerpo.put("messages", mensajes);
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                    .timeout(Duration.ofSeconds(45))
                    .header("x-api-key", claveClaude)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Claude respondió {}: {}", resp.statusCode(), recorte(resp.body()));
                throw new FalloProveedor(causaHttp(resp.statusCode(), resp.body()));
            }
            List<String> textos = new ArrayList<>();
            for (JsonNode bloque : mapper.readTree(resp.body()).path("content")) {
                if ("text".equals(bloque.path("type").asText())) textos.add(bloque.path("text").asText());
            }
            String texto = String.join("\n", textos).trim();
            if (texto.isEmpty()) throw new FalloProveedor("no devolvió texto");
            return texto;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalloProveedor("la consulta fue interrumpida");
        } catch (FalloProveedor e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo consultar Claude: {}", e.toString());
            throw new FalloProveedor("no se pudo conectar (" + e.getClass().getSimpleName() + ")");
        }
    }

    // ---------------------------------------------------------------- Ollama (API compatible con OpenAI)

    private String conOllama(String sistema, List<Map<String, String>> mensajes) throws FalloProveedor {
        try {
            List<Object> lista = new ArrayList<>();
            lista.add(Map.of("role", "system", "content", sistema));
            lista.addAll(mensajes);
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("model", modeloOllama);
            cuerpo.put("temperature", 0.3);
            cuerpo.put("max_tokens", 1000);
            cuerpo.put("messages", lista);
            String base = urlOllama.endsWith("/") ? urlOllama.substring(0, urlOllama.length() - 1) : urlOllama;
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Ollama respondió {}: {}", resp.statusCode(), recorte(resp.body()));
                throw new FalloProveedor(causaHttp(resp.statusCode(), resp.body()));
            }
            String texto = mapper.readTree(resp.body()).path("choices").path(0).path("message").path("content")
                    .asText("").trim();
            if (texto.isEmpty()) throw new FalloProveedor("no devolvió texto");
            return texto;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalloProveedor("la consulta fue interrumpida");
        } catch (FalloProveedor e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo consultar Ollama: {}", e.toString());
            throw new FalloProveedor("no se pudo conectar (" + e.getClass().getSimpleName() + ")");
        }
    }

    // ---------------------------------------------------------------- utilidades

    /** Causa legible de un error HTTP: usa el mensaje del proveedor si viene en el JSON. */
    private String causaHttp(int codigo, String cuerpo) {
        String detalle = "";
        try {
            JsonNode e = mapper.readTree(cuerpo).path("error");
            detalle = e.isTextual() ? e.asText() : e.path("message").asText("");
        } catch (Exception ignorada) {
            // cuerpo no JSON
        }
        String base = switch (codigo) {
            case 400 -> "solicitud rechazada";
            case 401, 403 -> "clave inválida o sin permiso";
            case 404 -> "modelo no encontrado";
            case 500, 503, 504 -> "el servicio de IA está saturado, intenta de nuevo en unos segundos";
            case 429 -> "se superó la cuota o el límite de uso";
            default -> "error " + codigo;
        };
        return detalle.isBlank() ? base : base + " (" + recorte(detalle) + ")";
    }

    private String recorte(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
