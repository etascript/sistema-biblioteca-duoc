package main.java.com.function;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.util.Optional;

public class UsuarioFunction {

    // --- READ ALL (Obtener Usuarios) ---
    @FunctionName("obtenerUsuarios")
    public HttpResponseMessage getUsuarios(
            @HttpTrigger(
                name = "req", 
                methods = {HttpMethod.GET}, 
                authLevel = AuthorizationLevel.ANONYMOUS, 
                route = "usuarios" // Esto crea la ruta /api/usuarios
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Ejecutando función GET de usuarios...");
        
        // TODO: Aquí luego conectaremos con Oracle. Por ahora, devolvemos un JSON estático.
        String jsonResponse = "[{\"id\": 1, \"nombre\": \"Juan Perez\", \"email\": \"juan@ejemplo.com\"}]";

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonResponse)
                .build();
    }

    // --- CREATE (Crear Usuario) ---
    @FunctionName("crearUsuario")
    public HttpResponseMessage crearUsuario(
            @HttpTrigger(
                name = "req", 
                methods = {HttpMethod.POST}, 
                authLevel = AuthorizationLevel.ANONYMOUS, 
                route = "usuarios"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Ejecutando función POST de usuarios...");
        
        // Obtenemos el JSON que envía el BFF
        String body = request.getBody().orElse("");

        // TODO: Lógica para guardar en Base de Datos

        return request.createResponseBuilder(HttpStatus.CREATED)
                .header("Content-Type", "application/json")
                .body("{\"mensaje\": \"Usuario procesado correctamente\"}")
                .build();
    }
}