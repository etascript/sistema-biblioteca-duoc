package com.duoc.bff_service.Services;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

@Service
public class BffService {

    // Spring inyecta las URLs desde el application.properties
    @Value("${api.usuarios.url}")
    private String usuariosUrl;

    @Value("${api.prestamos.url}")
    private String prestamosUrl;

    private final RestTemplate restTemplate;

    public BffService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    // Ejemplo: Orquestar llamada para obtener usuarios
    public Object getUsuarios() {
        return restTemplate.getForObject(usuariosUrl, Object.class);
    }

    public Object getPrestamos() {
        return restTemplate.getForObject(prestamosUrl, Object.class);
    }
    
    // Aquí agregarías los métodos para POST, PUT, DELETE (Crear, Actualizar, Borrar)
}