# Migraciones de la base del servidor

Las tablas viven en `db.py`. Cada cambio a una tabla necesita una migración nueva aquí, para
que la base de Neon cambie igual que el código.

- Crear o poner al día las tablas (en Neon o en tu SQLite): dentro de `backend/`, `alembic upgrade head`.
- Después de cambiar una tabla en `db.py`: `alembic revision --autogenerate -m "qué cambió"`, revisa el
  archivo que aparece en `versions/` y súbelo junto con el cambio. `test_migraciones.py` falla si se te olvida.
- Ver el SQL sin tocar la base: `alembic upgrade head --sql`.

La conexión sale de `DATABASE_URL`. Sin ella se usa `backend/cecapi.db` (SQLite).
