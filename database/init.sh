#!/bin/bash

# Esperar a que el PDB esté disponible
sleep 5

# Ejecutar el script SQL en el PDB XEPDB1 como SYSTEM
sqlplus -s SYSTEM/"${ORACLE_PASSWORD}"@//localhost:1521/XEPDB1 <<EOF
@/container-entrypoint-initdb.d/db.sql

-- Dar permisos al usuario biblioteca sobre las tablas
GRANT SELECT, INSERT, UPDATE, DELETE ON usuarios TO biblioteca;
GRANT SELECT, INSERT, UPDATE, DELETE ON libros TO biblioteca;
GRANT SELECT, INSERT, UPDATE, DELETE ON prestamos TO biblioteca;

-- Crear sinonimos para que biblioteca acceda sin prefijo de esquema
CREATE OR REPLACE PUBLIC SYNONYM usuarios FOR SYSTEM.usuarios;
CREATE OR REPLACE PUBLIC SYNONYM libros FOR SYSTEM.libros;
CREATE OR REPLACE PUBLIC SYNONYM prestamos FOR SYSTEM.prestamos;

COMMIT;
EXIT;
EOF
