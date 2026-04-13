package com.function;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;

import java.sql.*;
import java.util.*;

public class GraphQLOperacionesFunction {

    private static final Gson gson = new Gson();
    private static final GraphQL graphQL;

    private static final String SCHEMA_SDL = """
        type Query {
            _estado: String
        }

        type Mutation {
            crearCategoria(nombre: String!, descripcion: String): Categoria
            asignarCategoria(idLibro: Int!, idCategoria: Int!): Libro
            crearResena(idUsuario: Int!, idLibro: Int!, calificacion: Int!, comentario: String): Resena
            devolverLibro(idPrestamo: Int!): Prestamo
        }

        type Categoria {
            idCategoria: Int
            nombre:      String
            descripcion: String
        }

        type Libro {
            idLibro:    Int
            titulo:     String
            autor:      String
            disponible: Boolean
        }

        type Prestamo {
            idPrestamo:      Int
            idUsuario:       Int
            idLibro:         Int
            fechaPrestamo:   String
            fechaDevolucion: String
            estado:          String
        }

        type Resena {
            idResena:     Int
            idUsuario:    Int
            idLibro:      Int
            calificacion: Int
            comentario:   String
            fecha:        String
        }
    """;

    static {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(SCHEMA_SDL);

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
            .type("Query", b -> b
                .dataFetcher("_estado", env -> "GraphQL Operaciones activo")
            )
            .type("Mutation", b -> b
                .dataFetcher("crearCategoria",   env -> mutCrearCategoria(
                        env.getArgument("nombre"),
                        env.getArgument("descripcion")))
                .dataFetcher("asignarCategoria", env -> mutAsignarCategoria(
                        env.getArgument("idLibro"),
                        env.getArgument("idCategoria")))
                .dataFetcher("crearResena",      env -> mutCrearResena(
                        env.getArgument("idUsuario"),
                        env.getArgument("idLibro"),
                        env.getArgument("calificacion"),
                        env.getArgument("comentario")))
                .dataFetcher("devolverLibro",    env -> mutDevolverLibro(
                        env.getArgument("idPrestamo")))
            )
            .build();

        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
        graphQL = GraphQL.newGraphQL(schema).build();
    }

    // ── HTTP Trigger ──────────────────────────────────────────────────────────

    @FunctionName("graphqlOperaciones")
    public HttpResponseMessage execute(
            @HttpTrigger(name = "req",
                         methods = {HttpMethod.POST},
                         authLevel = AuthorizationLevel.ANONYMOUS,
                         route = "graphql/operaciones")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("POST /api/graphql/operaciones");

        String body = request.getBody().orElse("").trim();
        if (body.isEmpty()) {
            return badRequest(request, "El body no puede estar vacio. Envie {\"query\": \"mutation {...}\"}");
        }

        JsonObject json;
        try {
            json = gson.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            return badRequest(request, "JSON invalido: " + e.getMessage());
        }

        if (!json.has("query")) {
            return badRequest(request, "El campo 'query' es obligatorio");
        }

        String query = json.get("query").getAsString().trim();

        ExecutionResult result = graphQL.execute(
            ExecutionInput.newExecutionInput().query(query).build()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getData());
        if (!result.getErrors().isEmpty()) {
            response.put("errors", result.getErrors().stream().map(e -> e.getMessage()).toList());
        }

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(gson.toJson(response))
                .build();
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    private static Map<String, Object> mutCrearCategoria(String nombre, String descripcion) throws SQLException {
        String sql = "INSERT INTO categorias (nombre, descripcion) VALUES (?, ?)";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"id_categoria"})) {
            ps.setString(1, nombre);
            ps.setString(2, descripcion);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int newId = keys.next() ? keys.getInt(1) : 0;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idCategoria", newId);
            m.put("nombre",      nombre);
            m.put("descripcion", descripcion);
            return m;
        }
    }

    private static Map<String, Object> mutAsignarCategoria(Integer idLibro, Integer idCategoria) throws SQLException {
        if (idLibro == null || idCategoria == null) throw new RuntimeException("idLibro e idCategoria son obligatorios");

        String update = "UPDATE libros SET id_categoria = ? WHERE id_libro = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setInt(1, idCategoria);
            ps.setInt(2, idLibro);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new RuntimeException("Libro con id " + idLibro + " no encontrado");
        }

        // Devolver el libro actualizado
        String select = "SELECT id_libro, titulo, autor, disponible FROM libros WHERE id_libro = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setInt(1, idLibro);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("idLibro",    rs.getInt("id_libro"));
                m.put("titulo",     rs.getString("titulo"));
                m.put("autor",      rs.getString("autor"));
                m.put("disponible", rs.getInt("disponible") == 1);
                return m;
            }
        }
        return null;
    }

    private static Map<String, Object> mutCrearResena(Integer idUsuario, Integer idLibro,
                                                       Integer calificacion, String comentario) throws SQLException {
        if (calificacion == null || calificacion < 1 || calificacion > 5) {
            throw new RuntimeException("La calificacion debe ser un numero entre 1 y 5");
        }

        String sql = "INSERT INTO resenas (id_usuario, id_libro, calificacion, comentario) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"id_resena"})) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idLibro);
            ps.setInt(3, calificacion);
            ps.setString(4, comentario);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int newId = keys.next() ? keys.getInt(1) : 0;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idResena",    newId);
            m.put("idUsuario",   idUsuario);
            m.put("idLibro",     idLibro);
            m.put("calificacion", calificacion);
            m.put("comentario",  comentario);
            m.put("fecha",       java.time.LocalDate.now().toString());
            return m;
        }
    }

    private static Map<String, Object> mutDevolverLibro(Integer idPrestamo) throws SQLException {
        if (idPrestamo == null) throw new RuntimeException("idPrestamo es obligatorio");

        // Obtener id_libro del prestamo y verificar estado
        int idLibro;
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id_libro, estado FROM prestamos WHERE id_prestamo = ?")) {
            ps.setInt(1, idPrestamo);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new RuntimeException("Prestamo con id " + idPrestamo + " no encontrado");
            if ("DEVUELTO".equals(rs.getString("estado"))) throw new RuntimeException("El prestamo ya fue devuelto");
            idLibro = rs.getInt("id_libro");
        }

        // Marcar prestamo como devuelto
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE prestamos SET estado = 'DEVUELTO', fecha_devolucion = SYSDATE WHERE id_prestamo = ?")) {
            ps.setInt(1, idPrestamo);
            ps.executeUpdate();
        }

        // Marcar libro como disponible
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE libros SET disponible = 1 WHERE id_libro = ?")) {
            ps.setInt(1, idLibro);
            ps.executeUpdate();
        }

        // Devolver prestamo actualizado
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id_prestamo, id_usuario, id_libro, " +
                 "TO_CHAR(fecha_prestamo,'YYYY-MM-DD') fecha_prestamo, " +
                 "TO_CHAR(fecha_devolucion,'YYYY-MM-DD') fecha_devolucion, estado " +
                 "FROM prestamos WHERE id_prestamo = ?")) {
            ps.setInt(1, idPrestamo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("idPrestamo",      rs.getInt("id_prestamo"));
                m.put("idUsuario",       rs.getInt("id_usuario"));
                m.put("idLibro",         rs.getInt("id_libro"));
                m.put("fechaPrestamo",   rs.getString("fecha_prestamo"));
                m.put("fechaDevolucion", rs.getString("fecha_devolucion"));
                m.put("estado",          rs.getString("estado"));
                return m;
            }
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpResponseMessage badRequest(HttpRequestMessage<Optional<String>> req, String msg) {
        return req.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{\"error\": \"" + msg + "\"}")
                .build();
    }
}
