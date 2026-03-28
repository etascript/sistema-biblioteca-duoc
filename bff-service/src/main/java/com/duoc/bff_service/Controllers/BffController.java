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

    // --- Usuarios ---
    @GetMapping("/usuarios")
    public ResponseEntity<Object> listarUsuarios() {
        return ResponseEntity.ok(bffService.getUsuarios());
    }

    @PostMapping("/usuarios")
    public ResponseEntity<Object> crearUsuario(@RequestBody Object body) {
        return ResponseEntity.status(201).body(bffService.crearUsuario(body));
    }

    // --- Libros ---
    @GetMapping("/libros")
    public ResponseEntity<Object> listarLibros() {
        return ResponseEntity.ok(bffService.getLibros());
    }

    @PostMapping("/libros")
    public ResponseEntity<Object> crearLibro(@RequestBody Object body) {
        return ResponseEntity.status(201).body(bffService.crearLibro(body));
    }

    // --- Prestamos ---
    @GetMapping("/prestamos")
    public ResponseEntity<Object> listarPrestamos() {
        return ResponseEntity.ok(bffService.getPrestamos());
    }

    @PostMapping("/prestamos")
    public ResponseEntity<Object> crearPrestamo(@RequestBody Object body) {
        return ResponseEntity.status(201).body(bffService.crearPrestamo(body));
    }
}
