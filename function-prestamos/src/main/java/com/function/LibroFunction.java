package com.function;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LibroFunction {

    private final Gson gson = new Gson();

    @FunctionName("obtenerLibros")
    public HttpResponseMessage getLibros(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "libros")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("GET /api/libros - Consultando catalogo...");

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT id_libro, titulo, autor, disponible FROM libros ORDER BY id_libro")) {

            List<Map<String, Object>> libros = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> libro = new HashMap<>();
                libro.put("idLibro", rs.getInt("id_libro"));
                libro.put("titulo", rs.getString("titulo"));
                libro.put("autor", rs.getString("autor"));
                libro.put("disponible", rs.getInt("disponible") == 1);
                libros.add(libro);
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(libros))
                    .build();

        } catch (SQLException e) {
            context.getLogger().severe("Error de BD: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Error al consultar libros: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    @FunctionName("crearLibro")
    public HttpResponseMessage crearLibro(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "libros")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("POST /api/libros - Registrando libro...");

        String body = request.getBody().orElse("");
        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"El cuerpo de la solicitud no puede estar vacio\"}").build();
        }

        JsonObject json = gson.fromJson(body, JsonObject.class);
        String titulo = json.has("titulo") ? json.get("titulo").getAsString() : null;
        String autor = json.has("autor") ? json.get("autor").getAsString() : null;

        if (titulo == null || autor == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Los campos 'titulo' y 'autor' son obligatorios\"}").build();
        }

        String sql = "INSERT INTO libros (titulo, autor) VALUES (?, ?)";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"id_libro"})) {

            ps.setString(1, titulo);
            ps.setString(2, autor);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int newId = 0;
            if (keys.next()) {
                newId = keys.getInt(1);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Libro registrado correctamente");
            response.put("idLibro", newId);

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(response))
                    .build();

        } catch (SQLException e) {
            context.getLogger().severe("Error de BD: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Error al registrar libro: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
