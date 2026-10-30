"""Primera migración: uso_diario (lo que era uso_diario.json) y peticiones (sin texto ni IP).

Revision ID: 0001
Revises: 
Create Date: 2026-10-30 13:59:34.714870

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
    op.create_table('peticiones',
    sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
    sa.Column('creado_en', sa.DateTime(timezone=True), nullable=False),
    sa.Column('ruta', sa.String(length=20), nullable=False, comment='preguntar, entender, ver, documento'),
    sa.Column('resultado', sa.String(length=20), nullable=False, comment='respuesta, comando, bloqueado, error, no_disponible'),
    sa.Column('proveedor', sa.String(length=40), nullable=True, comment='NULL si no se llamó a la IA'),
    sa.Column('duracion_ms', sa.Integer(), nullable=False),
    sa.PrimaryKeyConstraint('id')
    )
    with op.batch_alter_table('peticiones', schema=None) as batch_op:
        batch_op.create_index(batch_op.f('ix_peticiones_creado_en'), ['creado_en'], unique=False)

    op.create_table('uso_diario',
    sa.Column('fecha', sa.String(length=10), nullable=False, comment='AAAA-MM-DD'),
    sa.Column('proveedor', sa.String(length=40), nullable=False, comment='groq, gemini, groq_vision'),
    sa.Column('peticiones', sa.Integer(), nullable=False),
    sa.Column('aviso_enviado', sa.Boolean(), nullable=False, comment='ya avisó del 80 por ciento'),
    sa.PrimaryKeyConstraint('fecha', 'proveedor')
    )


def downgrade() -> None:
    """Borra las tablas (pierde los conteos)."""
    op.drop_table('uso_diario')
    with op.batch_alter_table('peticiones', schema=None) as batch_op:
        batch_op.drop_index(batch_op.f('ix_peticiones_creado_en'))

    op.drop_table('peticiones')
