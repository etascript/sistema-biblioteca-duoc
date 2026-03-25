package com.duoc.bff_service.Controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.duoc.bff_service.Services.BffService;

@RestController
@RequestMapping("/api/bff")
public class BffController {

    private final BffService bffService;

    public BffController(BffService bffService) {
        this.bffService = bffService;
    }

    // Endpoint: GET /api/bff/usuarios
    @GetMapping("/usuarios")
    public ResponseEntity<Object> listarUsuarios() {
        return ResponseEntity.ok(bffService.getUsuarios());
    }

    // Endpoint: GET /api/bff/prestamos
    @GetMapping("/prestamos")
    public ResponseEntity<Object> listarPrestamos() {
        return ResponseEntity.ok(bffService.getPrestamos());
    }
}
