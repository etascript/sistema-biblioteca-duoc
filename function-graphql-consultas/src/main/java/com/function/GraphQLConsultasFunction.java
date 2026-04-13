package com.function;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;

import java.sql.*;
import java.util.*;

public class GraphQLConsultasFunction {

    private static final Gson gson = new Gson();
    private static final GraphQL graphQL;

    private static final String SCHEMA_SDL = """
        type Query {
            usuarios:                  [Usuario]
            usuario(id: Int):          Usuario
            libros:                    [Libro]
            libro(id: Int):            Libro
            categorias:                [Categoria]
            prestamosPorUsuario(idUsuario: Int): [Prestamo]
            resenasPorLibro(idLibro: Int):       [Resena]
            librosPorCategoria(idCategoria: Int): [Libro]
        }

        type Usuario {
            idUsuario:     Int
            nombre:        String
            email:         String
            fechaRegistro: String
            prestamos:     [Prestamo]
        }

        type Libro {
            idLibro:    Int
            titulo:     String
            autor:      String
            disponible: Boolean
            categoria:  Categoria
            resenas:    [Resena]
        }

        type Categoria {
            idCategoria:  Int
            nombre:       String
            descripcion:  String
        }

        type Prestamo {
            idPrestamo:      Int
            idUsuario:       Int
            nombreUsuario:   String
            idLibro:         Int
            tituloLibro:     String
            fechaPrestamo:   String
            fechaDevolucion: String
            estado:          String
        }

        type Resena {
            idResena:      Int
            idUsuario:     Int
            nombreUsuario: String
            idLibro:       Int
            tituloLibro:   String
            calificacion:  Int
            comentario:    String
            fecha:         String
        }
    """;

    static {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(SCHEMA_SDL);

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
            .type("Query", b -> b
                .dataFetcher("usuarios",            env -> fetchUsuarios())
                .dataFetcher("usuario",             env -> fetchUsuario(env.getArgument("id")))
                .dataFetcher("libros",              env -> fetchLibros())
                .dataFetcher("libro",               env -> fetchLibro(env.getArgument("id")))
                .dataFetcher("categorias",          env -> fetchCategorias())
                .dataFetcher("prestamosPorUsuario", env -> fetchPrestamosPorUsuario(env.getArgument("idUsuario")))
                .dataFetcher("resenasPorLibro",     env -> fetchResenasPorLibro(env.getArgument("idLibro")))
                .dataFetcher("librosPorCategoria",  env -> fetchLibrosPorCategoria(env.getArgument("idCategoria")))
            )
            .type("Usuario", b -> b
                .dataFetcher("prestamos", env -> {
                    Map<String, Object> u = env.getSource();
                    return fetchPrestamosPorUsuario((Integer) u.get("idUsuario"));
                })
            )
            .type("Libro", b -> b
                .dataFetcher("categoria", env -> {
                    Map<String, Object> l = env.getSource();
                    Integer idCat = (Integer) l.get("idCategoria");
                    return idCat != null ? fetchCategoria(idCat) : null;
                })
                .dataFetcher("resenas", env -> {
                    Map<String, Object> l = env.getSource();
                    return fetchResenasPorLibro((Integer) l.get("idLibro"));
                })
            )
            .build();

        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
        graphQL = GraphQL.newGraphQL(schema).build();
    }

    // ── HTTP Trigger ──────────────────────────────────────────────────────────

    @FunctionName("graphqlConsultas")
    public HttpResponseMessage execute(
            @HttpTrigger(name = "req",
                         methods = {HttpMethod.POST},
                         authLevel = AuthorizationLevel.ANONYMOUS,
                         route = "graphql/consultas")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("POST /api/graphql/consultas");

        String body = request.getBody().orElse("").trim();
        if (body.isEmpty()) {
            return badRequest(request, "El body no puede estar vacio. Envie {\"query\": \"...\"}");
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

        if (query.startsWith("mutation")) {
            return badRequest(request, "Este endpoint solo acepta queries. Use /api/graphql/operaciones para mutations.");
        }

        ExecutionResult result = graphQL.execute(query);

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

    // ── Data Fetchers ─────────────────────────────────────────────────────────

    private static List<Map<String, Object>> fetchUsuarios() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseHelper.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id_usuario, nombre, email, TO_CHAR(fecha_registro,'YYYY-MM-DD') fecha_registro " +
                 "FROM usuarios ORDER BY id_usuario")) {
            while (rs.next()) list.add(mapUsuario(rs));
        }
        return list;
    }

    private static Map<String, Object> fetchUsuario(Integer id) throws SQLException {
        if (id == null) return null;
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id_usuario, nombre, email, TO_CHAR(fecha_registro,'YYYY-MM-DD') fecha_registro " +
                 "FROM usuarios WHERE id_usuario = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapUsuario(rs) : null;
        }
    }

    private static List<Map<String, Object>> fetchLibros() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseHelper.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id_libro, titulo, autor, disponible, id_categoria FROM libros ORDER BY id_libro")) {
            while (rs.next()) list.add(mapLibro(rs));
        }
        return list;
    }

    private static Map<String, Object> fetchLibro(Integer id) throws SQLException {
        if (id == null) return null;
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id_libro, titulo, autor, disponible, id_categoria FROM libros WHERE id_libro = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapLibro(rs) : null;
        }
    }

    private static List<Map<String, Object>> fetchLibrosPorCategoria(Integer idCategoria) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        if (idCategoria == null) return list;
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id_libro, titulo, autor, disponible, id_categoria FROM libros WHERE id_categoria = ?")) {
            ps.setInt(1, idCategoria);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapLibro(rs));
        }
        return list;
    }

    private static List<Map<String, Object>> fetchCategorias() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseHelper.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id_categoria, nombre, descripcion FROM categorias ORDER BY id_categoria")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("idCategoria", rs.getInt("id_categoria"));
                row.put("nombre",      rs.getString("nombre"));
                row.put("descripcion", rs.getString("descripcion"));
                list.add(row);
            }
        }
        return list;
    }

    private static Map<String, Object> fetchCategoria(Integer id) throws SQLException {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id_categoria, nombre, descripcion FROM categorias WHERE id_categoria = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("idCategoria", rs.getInt("id_categoria"));
                row.put("nombre",      rs.getString("nombre"));
                row.put("descripcion", rs.getString("descripcion"));
                return row;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> fetchPrestamosPorUsuario(Integer idUsuario) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT p.id_prestamo, p.id_usuario, u.nombre nombre_usuario, " +
                     "p.id_libro, l.titulo titulo_libro, " +
                     "TO_CHAR(p.fecha_prestamo,'YYYY-MM-DD') fecha_prestamo, " +
                     "TO_CHAR(p.fecha_devolucion,'YYYY-MM-DD') fecha_devolucion, p.estado " +
                     "FROM prestamos p " +
                     "JOIN usuarios u ON p.id_usuario = u.id_usuario " +
                     "JOIN libros l ON p.id_libro = l.id_libro " +
                     (idUsuario != null ? "WHERE p.id_usuario = ? " : "") +
                     "ORDER BY p.id_prestamo";
        try (Connection conn = DatabaseHelper.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql);
            if (idUsuario != null) ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapPrestamo(rs));
        }
        return list;
    }

    private static List<Map<String, Object>> fetchResenasPorLibro(Integer idLibro) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT r.id_resena, r.id_usuario, u.nombre nombre_usuario, " +
                     "r.id_libro, l.titulo titulo_libro, r.calificacion, r.comentario, " +
                     "TO_CHAR(r.fecha,'YYYY-MM-DD') fecha " +
                     "FROM resenas r " +
                     "JOIN usuarios u ON r.id_usuario = u.id_usuario " +
                     "JOIN libros l ON r.id_libro = l.id_libro " +
                     (idLibro != null ? "WHERE r.id_libro = ? " : "") +
                     "ORDER BY r.fecha DESC";
        try (Connection conn = DatabaseHelper.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sql);
            if (idLibro != null) ps.setInt(1, idLibro);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapResena(rs));
        }
        return list;
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> mapUsuario(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idUsuario",     rs.getInt("id_usuario"));
        m.put("nombre",        rs.getString("nombre"));
        m.put("email",         rs.getString("email"));
        m.put("fechaRegistro", rs.getString("fecha_registro"));
        return m;
    }

    private static Map<String, Object> mapLibro(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idLibro",     rs.getInt("id_libro"));
        m.put("titulo",      rs.getString("titulo"));
        m.put("autor",       rs.getString("autor"));
        m.put("disponible",  rs.getInt("disponible") == 1);
        int idCat = rs.getInt("id_categoria");
        m.put("idCategoria", rs.wasNull() ? null : idCat);
        return m;
    }

    private static Map<String, Object> mapPrestamo(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idPrestamo",      rs.getInt("id_prestamo"));
        m.put("idUsuario",       rs.getInt("id_usuario"));
        m.put("nombreUsuario",   rs.getString("nombre_usuario"));
        m.put("idLibro",         rs.getInt("id_libro"));
        m.put("tituloLibro",     rs.getString("titulo_libro"));
        m.put("fechaPrestamo",   rs.getString("fecha_prestamo"));
        m.put("fechaDevolucion", rs.getString("fecha_devolucion"));
        m.put("estado",          rs.getString("estado"));
        return m;
    }

    private static Map<String, Object> mapResena(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idResena",      rs.getInt("id_resena"));
        m.put("idUsuario",     rs.getInt("id_usuario"));
        m.put("nombreUsuario", rs.getString("nombre_usuario"));
        m.put("idLibro",       rs.getInt("id_libro"));
        m.put("tituloLibro",   rs.getString("titulo_libro"));
        m.put("calificacion",  rs.getInt("calificacion"));
        m.put("comentario",    rs.getString("comentario"));
        m.put("fecha",         rs.getString("fecha"));
        return m;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpResponseMessage badRequest(HttpRequestMessage<Optional<String>> req, String msg) {
        return req.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{\"error\": \"" + msg + "\"}")
                .build();
    }
}
