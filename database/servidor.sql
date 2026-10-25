-- =============================================================================
-- CECAPI - Tablas del SERVIDOR del asistente (Módulo 3, carpeta backend/)
-- Dialecto SQLite, igual que database/schema.sql.
--
-- PROPUESTA: el servidor todavía no usa base de datos. Hoy solo guarda el
-- contador diario en backend/uso_diario.json y no guarda conversaciones.
-- Estas tablas son el diseño para cuando se guarden. Explicación y diagrama:
-- docs/referencia/tablas-servidor.md
--
-- No confundir con las tablas del Módulo 3 en schema.sql (consultas_ia,
-- respuestas_ia, contextos_conversacion): esas viven en el teléfono (Room).
-- Las claves de la IA NUNCA van en estas tablas: siguen solo en backend/.env.
-- =============================================================================

PRAGMA foreign_keys = ON;

-- Cada IA a la que el servidor le puede preguntar. Una fila por clase de
-- backend/proveedores.py; orden_respaldo es el orden de la lista PROVEEDORES.
CREATE TABLE proveedores (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre              TEXT NOT NULL UNIQUE,            -- 'gemini', 'groq' (Proveedor.nombre)
    modelo              TEXT NOT NULL,                   -- GEMINI_MODEL / GROQ_MODEL del .env
    orden_respaldo      INTEGER NOT NULL UNIQUE,         -- 1 = principal, 2 = primer respaldo...
    limite_diario       INTEGER NOT NULL DEFAULT 0,      -- *_LIMITE_DIARIO; 0 = sin límite conocido
    activo              INTEGER NOT NULL DEFAULT 1       -- boolean: 0/1 (1 si tiene clave en .env)
);

-- Persona que usa el asistente. nombre_usuario es el mismo de la app
-- (tabla usuarios de schema.sql); la contraseña NO se guarda aquí.
CREATE TABLE usuarios (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre_usuario      TEXT NOT NULL UNIQUE,
    ia_activada         INTEGER NOT NULL DEFAULT 0,      -- empieza apagada (decisión 0002)
    fecha_registro      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);

-- Una plática con el asistente: agrupa los mensajes que van como "contexto".
CREATE TABLE conversaciones (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id          INTEGER NOT NULL,
    titulo              TEXT,                            -- primera pregunta, recortada
    iniciada_en         INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    ultima_actividad    INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
CREATE INDEX idx_conversaciones_usuario ON conversaciones(usuario_id, ultima_actividad);

-- Lo que dijo la persona y lo que contestó el servidor (POST /preguntar y /entender).
CREATE TABLE mensajes (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    conversacion_id     INTEGER NOT NULL,
    rol                 TEXT NOT NULL CHECK (rol IN ('usuario', 'asistente')),
    tipo                TEXT NOT NULL CHECK (tipo IN ('pregunta', 'respuesta', 'comando', 'bloqueado', 'error')),
    texto               TEXT,                            -- NULL si se bloqueó: el dato sensible no se guarda
    comando             TEXT,                            -- storageCode (ENVIRONMENT...) si tipo = 'comando'
    proveedor_id        INTEGER,                         -- qué IA contestó; NULL si no hubo IA
    hay_mas             INTEGER NOT NULL DEFAULT 0,      -- boolean: la IA marcó MAS_DISPONIBLE
    duracion_ms         INTEGER,                         -- cuánto tardó la respuesta
    creado_en           INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    FOREIGN KEY (conversacion_id) REFERENCES conversaciones(id) ON DELETE CASCADE,
    FOREIGN KEY (proveedor_id) REFERENCES proveedores(id) ON DELETE SET NULL,
    CHECK (tipo <> 'comando' OR comando IS NOT NULL),
    CHECK (tipo <> 'bloqueado' OR texto IS NULL)
);
CREATE INDEX idx_mensajes_conversacion ON mensajes(conversacion_id, creado_en);
CREATE INDEX idx_mensajes_proveedor ON mensajes(proveedor_id);

-- Peticiones por día y proveedor. Reemplaza a backend/uso_diario.json
-- ({"fecha", "conteo": {proveedor: n}, "avisados": [...]}).
CREATE TABLE uso_diario (
    fecha               TEXT NOT NULL,                   -- 'AAAA-MM-DD', día de la computadora del servidor
    proveedor_id        INTEGER NOT NULL,
    peticiones          INTEGER NOT NULL DEFAULT 0,      -- cuentan aunque la IA falle
    aviso_enviado       INTEGER NOT NULL DEFAULT 0,      -- boolean: ya se avisó del 80% hoy
    PRIMARY KEY (fecha, proveedor_id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedores(id) ON DELETE CASCADE
);

-- Valores iniciales: los dos proveedores de hoy, con los límites de .env.example.
INSERT INTO proveedores (nombre, modelo, orden_respaldo, limite_diario) VALUES
    ('gemini', 'gemini-3.8-flash', 1, 250),
    ('groq', 'openai/gpt-oss-20b', 2, 1000);
