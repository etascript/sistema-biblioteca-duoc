package main.java.com.function;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.util.Optional;

public class LibroFunction {

    @FunctionName("obtenerLibros")
    public HttpResponseMessage getLibros(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "libros") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Consultando catálogo de libros...");
        String jsonResponse = "[{\"id\": 101, \"titulo\": \"El Quijote\", \"autor\": \"Cervantes\", \"disponible\": true}]";

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonResponse).build();
    }

    @FunctionName("crearLibro")
    public HttpResponseMessage crearLibro(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "libros") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        // Aquí iría la lógica para registrar un libro nuevo
        return request.createResponseBuilder(HttpStatus.CREATED)
                .header("Content-Type", "application/json")
                .body("{\"mensaje\": \"Libro registrado en el inventario\"}").build();
    }
}