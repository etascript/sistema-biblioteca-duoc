-- Migracion: columna stock en libros para manejo de inventario via eventos
ALTER TABLE libros ADD stock NUMBER DEFAULT 1 NOT NULL;

-- Sincronizar stock con el estado actual de disponible
UPDATE libros SET stock = disponible;

COMMIT;
