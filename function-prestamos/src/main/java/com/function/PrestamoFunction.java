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

public class PrestamoFunction {

    private final Gson gson = new Gson();

    @FunctionName("obtenerPrestamos")
    public HttpResponseMessage getPrestamos(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "prestamos")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("GET /api/prestamos - Consultando prestamos...");

        String sql = "SELECT p.id_prestamo, p.id_usuario, u.nombre AS nombre_usuario, " +
                     "p.id_libro, l.titulo AS titulo_libro, " +
                     "p.fecha_prestamo, p.fecha_devolucion, p.estado " +
                     "FROM prestamos p " +
                     "JOIN usuarios u ON p.id_usuario = u.id_usuario " +
                     "JOIN libros l ON p.id_libro = l.id_libro " +
                     "ORDER BY p.id_prestamo";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            List<Map<String, Object>> prestamos = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> prestamo = new HashMap<>();
                prestamo.put("idPrestamo", rs.getInt("id_prestamo"));
                prestamo.put("idUsuario", rs.getInt("id_usuario"));
                prestamo.put("nombreUsuario", rs.getString("nombre_usuario"));
                prestamo.put("idLibro", rs.getInt("id_libro"));
                prestamo.put("tituloLibro", rs.getString("titulo_libro"));
                prestamo.put("fechaPrestamo", rs.getString("fecha_prestamo"));
                prestamo.put("fechaDevolucion", rs.getString("fecha_devolucion"));
                prestamo.put("estado", rs.getString("estado"));
                prestamos.add(prestamo);
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(prestamos))
                    .build();

        } catch (SQLException e) {
            context.getLogger().severe("Error de BD: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Error al consultar prestamos: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    @FunctionName("crearPrestamo")
    public HttpResponseMessage crearPrestamo(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "prestamos")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("POST /api/prestamos - Creando prestamo...");

        String body = request.getBody().orElse("");
        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"El cuerpo de la solicitud no puede estar vacio\"}").build();
        }

        JsonObject json = gson.fromJson(body, JsonObject.class);
        int idUsuario = json.has("idUsuario") ? json.get("idUsuario").getAsInt() : 0;
        int idLibro = json.has("idLibro") ? json.get("idLibro").getAsInt() : 0;

        if (idUsuario == 0 || idLibro == 0) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Los campos 'idUsuario' e 'idLibro' son obligatorios\"}").build();
        }

        try (Connection conn = DatabaseHelper.getConnection()) {

            // Verificar que el libro existe y esta disponible
            PreparedStatement checkLibro = conn.prepareStatement(
                "SELECT disponible FROM libros WHERE id_libro = ?");
            checkLibro.setInt(1, idLibro);
            ResultSet rsLibro = checkLibro.executeQuery();

            if (!rsLibro.next()) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .body("{\"error\": \"El libro no existe\"}").build();
            }
            if (rsLibro.getInt("disponible") == 0) {
                return request.createResponseBuilder(HttpStatus.CONFLICT)
                        .header("Content-Type", "application/json")
                        .body("{\"error\": \"El libro no esta disponible\"}").build();
            }

            // Crear el prestamo
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO prestamos (id_usuario, id_libro) VALUES (?, ?)",
                new String[]{"id_prestamo"});
            ps.setInt(1, idUsuario);
            ps.setInt(2, idLibro);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int newId = 0;
            if (keys.next()) {
                newId = keys.getInt(1);
            }

            // Marcar el libro como no disponible
            PreparedStatement updateLibro = conn.prepareStatement(
                "UPDATE libros SET disponible = 0 WHERE id_libro = ?");
            updateLibro.setInt(1, idLibro);
            updateLibro.executeUpdate();

            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Prestamo creado correctamente");
            response.put("idPrestamo", newId);

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(response))
                    .build();

        } catch (SQLException e) {
            context.getLogger().severe("Error de BD: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Error al crear prestamo: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
