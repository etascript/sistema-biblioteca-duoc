package com.function;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class EventGridPublicadorFunction {

    private final Gson gson = new Gson();

    private static final String TOPIC_ENDPOINT = System.getenv("EVENT_GRID_TOPIC_ENDPOINT");
    private static final String TOPIC_KEY      = System.getenv("EVENT_GRID_TOPIC_KEY");

    @FunctionName("publicarEventoBiblioteca")
    public HttpResponseMessage publicarEvento(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "eventos/publicar")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("POST /api/eventos/publicar - Publicando evento en Event Grid...");

        String body = request.getBody().orElse("");
        if (body.isEmpty()) {
            return error(request, HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no puede estar vacio");
        }

        JsonObject json;
        try {
            json = gson.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            return error(request, HttpStatus.BAD_REQUEST, "JSON invalido: " + e.getMessage());
        }

        if (!json.has("tipo") || !json.has("asunto")) {
            return error(request, HttpStatus.BAD_REQUEST,
                "Los campos 'tipo' y 'asunto' son obligatorios. " +
                "Tipos validos: PRESTAMO_CREADO, LIBRO_DEVUELTO, USUARIO_REGISTRADO");
        }

        String tipo   = json.get("tipo").getAsString();
        String asunto = json.get("asunto").getAsString();

        Map<String, Object> datos = new HashMap<>();
        if (json.has("datos") && json.get("datos").isJsonObject()) {
            JsonObject datosJson = json.getAsJsonObject("datos");
            datosJson.entrySet().forEach(e -> datos.put(e.getKey(), e.getValue().getAsString()));
        }

        String tipoEvento = mapearTipoEvento(tipo);
        if (tipoEvento == null) {
            return error(request, HttpStatus.BAD_REQUEST,
                "Tipo de evento no reconocido: '" + tipo + "'. " +
                "Valores permitidos: PRESTAMO_CREADO, LIBRO_DEVUELTO, USUARIO_REGISTRADO");
        }

        if (TOPIC_ENDPOINT == null || TOPIC_ENDPOINT.isBlank()
                || TOPIC_ENDPOINT.startsWith("<")) {
            return error(request, HttpStatus.SERVICE_UNAVAILABLE,
                "EVENT_GRID_TOPIC_ENDPOINT no configurado. " +
                "Configure la variable de entorno con la URL del Custom Topic de Azure Event Grid.");
        }
        if (TOPIC_KEY == null || TOPIC_KEY.isBlank() || TOPIC_KEY.startsWith("<")) {
            return error(request, HttpStatus.SERVICE_UNAVAILABLE,
                "EVENT_GRID_TOPIC_KEY no configurado. " +
                "Configure la variable de entorno con la clave de acceso del Custom Topic.");
        }

        try {
            EventGridPublisherClient<EventGridEvent> client = new EventGridPublisherClientBuilder()
                    .endpoint(TOPIC_ENDPOINT)
                    .credential(new AzureKeyCredential(TOPIC_KEY))
                    .buildEventGridEventPublisherClient();

            EventGridEvent evento = new EventGridEvent(
                    asunto,
                    tipoEvento,
                    BinaryData.fromObject(datos),
                    "1.0"
            );
            evento.setId(UUID.randomUUID().toString());

            client.sendEvent(evento);

            context.getLogger().info("Evento publicado: tipo=" + tipoEvento + " asunto=" + asunto + " id=" + evento.getId());

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("mensaje", "Evento publicado correctamente en Azure Event Grid");
            respuesta.put("idEvento", evento.getId());
            respuesta.put("tipoEvento", tipoEvento);
            respuesta.put("asunto", asunto);
            respuesta.put("datos", datos);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(respuesta))
                    .build();

        } catch (Exception e) {
            context.getLogger().severe("Error al publicar evento: " + e.getMessage());
            return error(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Error al publicar evento en Event Grid: " + e.getMessage());
        }
    }

    private String mapearTipoEvento(String tipo) {
        return switch (tipo.toUpperCase()) {
            case "PRESTAMO_CREADO"    -> "biblioteca.prestamo.creado";
            case "LIBRO_DEVUELTO"     -> "biblioteca.prestamo.devuelto";
            case "USUARIO_REGISTRADO" -> "biblioteca.usuario.registrado";
            default -> null;
        };
    }

    private HttpResponseMessage error(HttpRequestMessage<?> req, HttpStatus status, String mensaje) {
        return req.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .body("{\"error\": \"" + mensaje + "\"}")
                .build();
    }
}
