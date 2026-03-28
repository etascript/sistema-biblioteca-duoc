package com.duoc.bff_service.Services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

@Service
public class BffService {

    @Value("${api.usuarios.url}")
    private String usuariosUrl;

    @Value("${api.prestamos.url}")
    private String prestamosUrl;

    private final RestTemplate restTemplate;

    public BffService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    // --- Usuarios ---
    public Object getUsuarios() {
        return restTemplate.getForObject(usuariosUrl, Object.class);
    }

    public Object crearUsuario(Object body) {
        return restTemplate.postForObject(usuariosUrl, body, Object.class);
    }

    // --- Libros (misma Function App que prestamos, ruta /api/libros) ---
    public Object getLibros() {
        String librosUrl = prestamosUrl.replace("/api/prestamos", "/api/libros");
        return restTemplate.getForObject(librosUrl, Object.class);
    }

    public Object crearLibro(Object body) {
        String librosUrl = prestamosUrl.replace("/api/prestamos", "/api/libros");
        return restTemplate.postForObject(librosUrl, body, Object.class);
    }

    // --- Prestamos ---
    public Object getPrestamos() {
        return restTemplate.getForObject(prestamosUrl, Object.class);
    }

    public Object crearPrestamo(Object body) {
        return restTemplate.postForObject(prestamosUrl, body, Object.class);
    }
}
