package com.cecapi.app.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2: los usuarios obtienen un rol y un origen/institución, y el nombre de usuario deja de distinguir mayúsculas (se vuelve MAYÚSCULAS). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN rol TEXT NOT NULL DEFAULT 'usuario'")
        db.execSQL("ALTER TABLE usuarios ADD COLUMN origen TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE usuarios SET nombre_usuario = UPPER(nombre_usuario)")
        // La cuenta de prueba compartida que ya existía se vuelve administradora.
        db.execSQL("UPDATE usuarios SET rol = 'administrador', origen = 'admin' WHERE nombre_usuario = 'CECAPI'")
    }
}

/**
 * v3: los ejercicios de audio del Módulo 6 ahora llevan un archivo de sonido y el volumen de cada oído, y los
 * ejercicios de vibración obtienen sus propias tablas. Los ejercicios viejos de comando de voz se descartan (sus
 * resultados se van con ellos, por cascada); el banco nuevo se crea la primera vez que se abre Actividades.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM resultados_ejercicios")
        db.execSQL("DELETE FROM ejercicios")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN archivo_sonido TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_izq_inicio REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_der_inicio REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_izq_fin REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_der_fin REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN duracion_ms INTEGER NOT NULL DEFAULT 1500")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ejercicios_vibracion (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "titulo TEXT NOT NULL, instruccion TEXT NOT NULL, respuesta_correcta TEXT NOT NULL, " +
                "nivel INTEGER NOT NULL, patron_ms TEXT NOT NULL, intensidad INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS resultados_vibracion (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "usuario_id INTEGER NOT NULL, ejercicio_vibracion_id INTEGER NOT NULL, " +
                "fue_correcto INTEGER NOT NULL, fecha_realizado INTEGER NOT NULL, " +
                "FOREIGN KEY(usuario_id) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(ejercicio_vibracion_id) REFERENCES ejercicios_vibracion(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_resultados_vibracion_usuario_id ON resultados_vibracion (usuario_id)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_resultados_vibracion_ejercicio_vibracion_id " +
                "ON resultados_vibracion (ejercicio_vibracion_id)",
        )
    }
}

/** v4: los roles obtienen educador, y un alumno puede vincularse al educador que le da clase. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN educador_id INTEGER")
    }
}

/** v5: una forma simple para que un alumno o un usuario independiente reporte un problema. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS incidencias (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "reportante_id INTEGER NOT NULL, destino_origen TEXT, mensaje TEXT NOT NULL, " +
                "fecha_reporte INTEGER NOT NULL, resuelta INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(reportante_id) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_incidencias_reportante_id ON incidencias (reportante_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_incidencias_destino_origen ON incidencias (destino_origen)")
    }
}

/** v6: un apodo opcional, que se muestra en la clasificación en vez del nombre real (hay menores de edad). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN apodo TEXT")
    }
}

/**
 * v7: quién en la cadena arriba de una cuenta (administrador > directivo > educador > alumno, o
 * administrador > usuario) todavía necesita revisarla. La columna nueva usa true por defecto para que
 * ninguna cuenta que ya existía antes de esta migración quede marcada como pendiente de forma retroactiva;
 * solo las cuentas creadas a partir de aquí usan false por defecto (SessionRepository.register lo fija explícitamente de cualquier forma).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN validado INTEGER NOT NULL DEFAULT 1")
    }
}

/** v8: en Clasificación, por default se muestra el nombre; la persona puede elegir mostrar su apodo. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN usar_apodo_ranking INTEGER NOT NULL DEFAULT 0")
    }
}

/** v9: fecha de nacimiento, para saber si la cuenta es de un menor de edad. Nula en cuentas que ya existían. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN fecha_nacimiento INTEGER")
    }
}

/** v10: el banco de ejercicios del Módulo 6 cambió (un sonido por oído, sin los de enfrente y atrás). Se borra para que se vuelva a cargar. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM resultados_ejercicios")
        db.execSQL("DELETE FROM ejercicios")
        db.execSQL("DELETE FROM resultados_vibracion")
        db.execSQL("DELETE FROM ejercicios_vibracion")
    }
}

/** v11: correo opcional de contacto, solicitud de validación institucional, baja pedida por menores y bitácora de cambios de cuenta. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN email TEXT")
        db.execSQL("ALTER TABLE usuarios ADD COLUMN solicita_validacion INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE usuarios ADD COLUMN solicita_baja INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE usuarios ADD COLUMN aviso_pendiente TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cambios_cuenta (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "objetivo_id INTEGER NOT NULL, autor_id INTEGER, tipo TEXT NOT NULL, detalle TEXT, " +
                "fecha INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cambios_cuenta_objetivo_id ON cambios_cuenta (objetivo_id)")
    }
}

/** v12: cada cuenta nueva queda "nueva" hasta que un administrador la confirma; las de antes ya se consideran confirmadas. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN revision_cuenta TEXT NOT NULL DEFAULT 'confirmada'")
    }
}

/** v13: la institución a la que la persona pidió unirse, separada de la institución ya validada (origen). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN institucion_solicitada TEXT")
    }
}

/** v14: contactos de emergencia por persona, y reportes con destinatario y opción de anonimato. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS contactos_emergencia (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, usuario_id INTEGER NOT NULL, " +
                "nombre TEXT NOT NULL, telefono TEXT NOT NULL, relacion TEXT, " +
                "FOREIGN KEY(usuario_id) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contactos_emergencia_usuario_id ON contactos_emergencia (usuario_id)")
        db.execSQL("ALTER TABLE incidencias ADD COLUMN destinatario_rol TEXT")
        db.execSQL("ALTER TABLE incidencias ADD COLUMN anonimo INTEGER NOT NULL DEFAULT 0")
    }
}

/** v15: un alumno puede pedir cambiar de educador; queda marcado hasta que lo atienda quien corresponde. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN solicita_cambio_educador INTEGER NOT NULL DEFAULT 0")
    }
}

/** v16: el administrador puede responder un mensaje de Ayuda y soporte; la respuesta la ve quien lo escribió. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE incidencias ADD COLUMN respuesta TEXT")
    }
}

/**
 * v17: ya no hay menores de edad (todas las personas usuarias son adultas) y el Centro de Solicitudes por
 * plantillas y Emergencias se quitaron.
 *  - usuarios pierde fecha_nacimiento y solicita_baja (la baja pedida por un menor).
 *  - Se borran las tablas de plantillas, solicitudes y contactos de emergencia.
 *
 * SQLite no puede quitar columnas en las versiones de Android que soportamos, así que usuarios se reconstruye
 * (crear la nueva, copiar, borrar la vieja y renombrar). Room activa las llaves foráneas hasta onOpen, después
 * de migrar, por eso borrar la tabla vieja no arrastra en cascada los datos que dependen de cada cuenta.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS datos_solicitud")
        db.execSQL("DROP TABLE IF EXISTS solicitudes_generadas")
        db.execSQL("DROP TABLE IF EXISTS plantillas_solicitud")
        db.execSQL("DROP TABLE IF EXISTS contactos_emergencia")

        val columnas = "id, nombre_usuario, contrasena_hash, nombre_completo, fecha_registro, rol, origen, educador_id, " +
            "apodo, validado, usar_apodo_ranking, email, solicita_validacion, aviso_pendiente, revision_cuenta, " +
            "institucion_solicitada, solicita_cambio_educador"
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `usuarios_nueva` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`nombre_usuario` TEXT NOT NULL, `contrasena_hash` TEXT NOT NULL, `nombre_completo` TEXT NOT NULL, " +
                "`fecha_registro` INTEGER NOT NULL, `rol` TEXT NOT NULL, `origen` TEXT NOT NULL, `educador_id` INTEGER, " +
                "`apodo` TEXT, `validado` INTEGER NOT NULL, `usar_apodo_ranking` INTEGER NOT NULL, `email` TEXT, " +
                "`solicita_validacion` INTEGER NOT NULL, `aviso_pendiente` TEXT, `revision_cuenta` TEXT NOT NULL, " +
                "`institucion_solicitada` TEXT, `solicita_cambio_educador` INTEGER NOT NULL)",
        )
        db.execSQL("INSERT INTO usuarios_nueva ($columnas) SELECT $columnas FROM usuarios")
        db.execSQL("DROP TABLE usuarios")
        db.execSQL("ALTER TABLE usuarios_nueva RENAME TO usuarios")
    }
}
