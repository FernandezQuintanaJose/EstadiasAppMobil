"""Primera migración: conversaciones, mensajes (texto solo con GUARDAR_TEXTOS=1) y uso_diario.

Revision ID: 0001
Revises: 
Create Date: 2026-10-30 14:43:31.833361

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '0001'
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Crea las tablas."""
    op.create_table('conversaciones',
    sa.Column('id', sa.String(length=36), nullable=False, comment='UUID al azar: el conversacionId de la API'),
    sa.Column('usuario_id', sa.Integer(), nullable=True, comment='NULL hasta que el servidor tenga cuentas'),
    sa.Column('titulo', sa.String(length=60), nullable=True, comment='primera pregunta; NULL si GUARDAR_TEXTOS=0'),
    sa.Column('iniciada_en', sa.DateTime(timezone=True), nullable=False),
    sa.Column('ultima_actividad', sa.DateTime(timezone=True), nullable=False),
    sa.PrimaryKeyConstraint('id')
    )
    with op.batch_alter_table('conversaciones', schema=None) as batch_op:
        batch_op.create_index('ix_conversaciones_usuario', ['usuario_id', 'ultima_actividad'], unique=False)

    op.create_table('uso_diario',
    sa.Column('fecha', sa.String(length=10), nullable=False, comment='AAAA-MM-DD'),
    sa.Column('proveedor', sa.String(length=40), nullable=False, comment='groq, gemini, groq_vision'),
    sa.Column('peticiones', sa.Integer(), nullable=False),
    sa.Column('aviso_enviado', sa.Boolean(), nullable=False, comment='ya avisó del 80 por ciento'),
    sa.PrimaryKeyConstraint('fecha', 'proveedor')
    )
    op.create_table('mensajes',
    sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
    sa.Column('conversacion_id', sa.String(length=36), nullable=False),
    sa.Column('ruta', sa.String(length=20), nullable=False, comment='preguntar, entender, ver o documento'),
    sa.Column('rol', sa.String(length=10), nullable=False),
    sa.Column('tipo', sa.String(length=10), nullable=False),
    sa.Column('texto', sa.Text(), nullable=True, comment='NULL si se bloqueó o si GUARDAR_TEXTOS=0'),
    sa.Column('comando', sa.String(length=40), nullable=True, comment='storageCode si tipo = comando'),
    sa.Column('proveedor', sa.String(length=40), nullable=True, comment='IA que contestó; NULL si no hubo IA'),
    sa.Column('hay_mas', sa.Boolean(), nullable=False),
    sa.Column('duracion_ms', sa.Integer(), nullable=True, comment='cuánto tardó la IA'),
    sa.Column('creado_en', sa.DateTime(timezone=True), nullable=False),
    sa.CheckConstraint("rol IN ('usuario', 'asistente')", name='ck_mensajes_rol'),
    sa.CheckConstraint("ruta IN ('preguntar', 'entender', 'ver', 'documento')", name='ck_mensajes_ruta'),
    sa.CheckConstraint("tipo <> 'bloqueado' OR texto IS NULL", name='ck_mensajes_bloqueado_sin_texto'),
    sa.CheckConstraint("tipo <> 'comando' OR comando IS NOT NULL", name='ck_mensajes_comando'),
    sa.CheckConstraint("tipo IN ('pregunta', 'respuesta', 'comando', 'bloqueado', 'error')", name='ck_mensajes_tipo'),
    sa.ForeignKeyConstraint(['conversacion_id'], ['conversaciones.id'], ondelete='CASCADE'),
    sa.PrimaryKeyConstraint('id')
    )
    with op.batch_alter_table('mensajes', schema=None) as batch_op:
        batch_op.create_index('ix_mensajes_conversacion', ['conversacion_id', 'creado_en'], unique=False)
        batch_op.create_index('ix_mensajes_creado', ['creado_en'], unique=False)



def downgrade() -> None:
    """Borra las tablas (pierde el historial y los conteos)."""
    with op.batch_alter_table('mensajes', schema=None) as batch_op:
        batch_op.drop_index('ix_mensajes_creado')
        batch_op.drop_index('ix_mensajes_conversacion')

    op.drop_table('mensajes')
    op.drop_table('uso_diario')
    with op.batch_alter_table('conversaciones', schema=None) as batch_op:
        batch_op.drop_index('ix_conversaciones_usuario')

    op.drop_table('conversaciones')
