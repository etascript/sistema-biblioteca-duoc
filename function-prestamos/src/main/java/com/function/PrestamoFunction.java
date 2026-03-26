package main.java.com.function;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.util.Optional;

public class PrestamoFunction {

    @FunctionName("obtenerPrestamos")
    public HttpResponseMessage getPrestamos(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "prestamos") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Consultando historial de préstamos...");
        String jsonResponse = "[{\"idPrestamo\": 501, \"idUsuario\": 1, \"idLibro\": 101, \"fecha\": \"2024-05-20\"}]";

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonResponse).build();
    }

    @FunctionName("crearPrestamo")
    public HttpResponseMessage crearPrestamo(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "prestamos") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        // Aquí iría la lógica transaccional: Validar que el libro existe, que está disponible, y asignarlo al usuario
        return request.createResponseBuilder(HttpStatus.CREATED)
                .header("Content-Type", "application/json")
                .body("{\"mensaje\": \"Préstamo procesado con éxito\"}").build();
    }
}