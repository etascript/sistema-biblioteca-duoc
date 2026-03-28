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

public class UsuarioFunction {

    private final Gson gson = new Gson();

    @FunctionName("obtenerUsuarios")
    public HttpResponseMessage getUsuarios(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "usuarios"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("GET /api/usuarios - Consultando base de datos...");

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT id_usuario, nombre, email, fecha_registro FROM usuarios ORDER BY id_usuario")) {

            List<Map<String, Object>> usuarios = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> usuario = new HashMap<>();
                usuario.put("idUsuario", rs.getInt("id_usuario"));
                usuario.put("nombre", rs.getString("nombre"));
                usuario.put("email", rs.getString("email"));
                usuario.put("fechaRegistro", rs.getString("fecha_registro"));
                usuarios.add(usuario);
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(usuarios))
                    .build();

        } catch (SQLException e) {
            context.getLogger().severe("Error de BD: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Error al consultar usuarios: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    @FunctionName("crearUsuario")
    public HttpResponseMessage crearUsuario(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "usuarios"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("POST /api/usuarios - Creando usuario...");

        String body = request.getBody().orElse("");
        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"El cuerpo de la solicitud no puede estar vacio\"}").build();
        }

        JsonObject json = gson.fromJson(body, JsonObject.class);
        String nombre = json.has("nombre") ? json.get("nombre").getAsString() : null;
        String email = json.has("email") ? json.get("email").getAsString() : null;

        if (nombre == null || email == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Los campos 'nombre' y 'email' son obligatorios\"}").build();
        }

        String sql = "INSERT INTO usuarios (nombre, email) VALUES (?, ?)";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"id_usuario"})) {

            ps.setString(1, nombre);
            ps.setString(2, email);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int newId = 0;
            if (keys.next()) {
                newId = keys.getInt(1);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Usuario creado correctamente");
            response.put("idUsuario", newId);

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(response))
                    .build();

        } catch (SQLException e) {
            context.getLogger().severe("Error de BD: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Error al crear usuario: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
