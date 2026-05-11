package com.function;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EventGridSuscriptorFunction {

    private final Gson gson = new Gson();

    /**
     * Función disparada por Azure Event Grid.
     *
     * Azure Event Grid envía un HTTP POST al endpoint del runtime de Azure Functions:
     *   POST {functionUrl}/runtime/webhooks/EventGrid?functionName=procesarEventoBiblioteca
     *
     * El runtime maneja automáticamente el handshake de validación de suscripción
     * (SubscriptionValidationEvent) antes de comenzar a entregar eventos reales.
     *
     * El parámetro 'event' recibe el JSON completo del EventGridEvent publicado.
     */
    @FunctionName("procesarEventoBiblioteca")
    public void procesarEvento(
            @EventGridTrigger(name = "event") String event,
            final ExecutionContext context) {

        context.getLogger().info("EventGrid Trigger - Evento recibido: " + event);

        try {
            JsonObject eventoJson = gson.fromJson(event, JsonObject.class);

            String tipoEvento = eventoJson.has("eventType")
                    ? eventoJson.get("eventType").getAsString() : "desconocido";
            String asunto     = eventoJson.has("subject")
                    ? eventoJson.get("subject").getAsString() : "";
            String idEvento   = eventoJson.has("id")
                    ? eventoJson.get("id").getAsString() : "";

            String datos = "";
            if (eventoJson.has("data")) {
                JsonElement dataElement = eventoJson.get("data");
                datos = dataElement.isJsonNull() ? "" : dataElement.toString();
            }

            switch (tipoEvento) {
                case "biblioteca.prestamo.creado"   -> procesarPrestamoCreado(eventoJson, context);
                case "biblioteca.usuario.eliminado" -> procesarUsuarioEliminado(eventoJson, context);
                default -> context.getLogger().info("Evento sin logica especifica: " + tipoEvento);
            }

            guardarEventoEnDB(idEvento, tipoEvento, asunto, datos, context);

            context.getLogger().info("Evento procesado correctamente: tipo=" + tipoEvento
                    + " asunto=" + asunto + " id=" + idEvento);

        } catch (Exception e) {
            context.getLogger().severe("Error al procesar evento: " + e.getMessage());
            throw new RuntimeException("Fallo al procesar evento de Event Grid", e);
        }
    }

    /**
     * Endpoint HTTP para consultar el historial de eventos recibidos desde Event Grid.
     * Permite verificar que los eventos están siendo procesados correctamente.
     */
    @FunctionName("consultarEventosLog")
    public HttpResponseMessage consultarEventosLog(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "eventos/log")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("GET /api/eventos/log - Consultando historial de eventos...");

        String tipoFiltro = request.getQueryParameters().get("tipo");

        String sql = "SELECT id_evento, tipo_evento, asunto, datos, fecha_recibido " +
                     "FROM eventos_log " +
                     (tipoFiltro != null ? "WHERE tipo_evento = ? " : "") +
                     "ORDER BY fecha_recibido DESC";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (tipoFiltro != null) {
                ps.setString(1, tipoFiltro);
            }

            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> eventos = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> e = new HashMap<>();
                e.put("idEvento",       rs.getLong("id_evento"));
                e.put("tipoEvento",     rs.getString("tipo_evento"));
                e.put("asunto",         rs.getString("asunto"));
                e.put("datos",          rs.getString("datos"));
                e.put("fechaRecibido",  rs.getString("fecha_recibido"));
                eventos.add(e);
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(eventos))
                    .build();

        } catch (SQLException e) {
            context.getLogger().severe("Error de BD: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Error al consultar eventos_log: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    private void procesarPrestamoCreado(JsonObject eventoJson, ExecutionContext context) {
        JsonObject data = eventoJson.getAsJsonObject("data");
        if (data == null || !data.has("idLibro")) {
            context.getLogger().warning("PRESTAMO_CREADO sin idLibro en data, omitiendo.");
            return;
        }
        int idLibro = Integer.parseInt(data.get("idLibro").getAsString());

        String sql = "UPDATE libros SET stock = stock - 1, " +
                     "disponible = CASE WHEN stock - 1 <= 0 THEN 0 ELSE 1 END " +
                     "WHERE id_libro = ? AND stock > 0";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idLibro);
            int rows = ps.executeUpdate();
            context.getLogger().info("Stock decrementado para libro " + idLibro + " (" + rows + " fila/s)");
        } catch (SQLException e) {
            context.getLogger().severe("Error al decrementar stock: " + e.getMessage());
            throw new RuntimeException("Error al procesar PRESTAMO_CREADO", e);
        }
    }

    private void procesarUsuarioEliminado(JsonObject eventoJson, ExecutionContext context) {
        JsonObject data = eventoJson.getAsJsonObject("data");
        if (data == null || !data.has("idUsuario")) {
            context.getLogger().warning("USUARIO_ELIMINADO sin idUsuario en data, omitiendo.");
            return;
        }
        int idUsuario = Integer.parseInt(data.get("idUsuario").getAsString());

        String restoreStock = "UPDATE libros SET stock = stock + 1, disponible = 1 " +
                              "WHERE id_libro IN (" +
                              "  SELECT id_libro FROM prestamos WHERE id_usuario = ? AND estado = 'ACTIVO')";
        String deletePrestamos = "DELETE FROM prestamos WHERE id_usuario = ?";

        try (Connection conn = DatabaseHelper.getConnection()) {
            PreparedStatement ps1 = conn.prepareStatement(restoreStock);
            ps1.setInt(1, idUsuario);
            ps1.executeUpdate();

            PreparedStatement ps2 = conn.prepareStatement(deletePrestamos);
            ps2.setInt(1, idUsuario);
            int deleted = ps2.executeUpdate();
            context.getLogger().info("Prestamos eliminados para usuario " + idUsuario + ": " + deleted);
        } catch (SQLException e) {
            context.getLogger().severe("Error al limpiar prestamos: " + e.getMessage());
            throw new RuntimeException("Error al procesar USUARIO_ELIMINADO", e);
        }
    }

    private void guardarEventoEnDB(String idEventoExterno, String tipoEvento,
                                    String asunto, String datos,
                                    ExecutionContext context) {
        String sql = "INSERT INTO eventos_log (id_evento_externo, tipo_evento, asunto, datos) " +
                     "VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idEventoExterno);
            ps.setString(2, tipoEvento);
            ps.setString(3, asunto);
            ps.setString(4, datos);
            ps.executeUpdate();

            context.getLogger().info("Evento guardado en eventos_log: " + tipoEvento);

        } catch (SQLException e) {
            context.getLogger().severe("Error al guardar evento en BD: " + e.getMessage());
            throw new RuntimeException("Error al persistir evento", e);
        }
    }
}
