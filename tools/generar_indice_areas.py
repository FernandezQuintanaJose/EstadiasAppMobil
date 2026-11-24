#!/usr/bin/env python3
"""
Genera el índice de áreas del CECAPI a partir de las fotografías de referencia.

Uso típico:

    python tools/generar_indice_areas.py \
        --refs /home/jesus62/Escritorio/Referencias_CECAPI

El script NO copia ni modifica las fotografías. Solo escribe:

    app/src/main/assets/cecapi_areas/areas.json
    app/src/main/assets/cecapi_areas/embeddings.bin

Requisitos de desarrollo (no se agregan al APK):
    python3.11 -m venv .venv-areas
    .venv-areas/bin/pip install "numpy<2" tflite-runtime pillow

El modelo debe estar en:
    app/src/main/assets/cecapi_areas/embedder.tflite
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image
import tflite_runtime.interpreter as tflite

SIZE = 224
NORMALIZATION = "0_1"
NORMALIZATION_CODE = 0
THRESHOLD = 0.80
MARGIN = 0.08
TOP_K = 3
EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
MAGIC = b"CAPI"
VERSION = 1

FRASES = {
    "Area_de_Juegos": "el área de juegos",
    "Bienvenida": "el área de bienvenida",
    "Camino_De_Entrada": "el camino de entrada",
    "Edificio_Principal": "el edificio principal",
    "Edificio_Recreativo": "el edificio recreativo",
    "Edificio_Recreativo_Dentro": "el interior del edificio recreativo",
    "Pastizal": "el pastizal",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            chunk = stream.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def embed(interpreter, input_detail, output_detail, path: Path) -> np.ndarray:
    image = Image.open(path).convert("RGB").resize(
        (SIZE, SIZE), Image.Resampling.BILINEAR
    )
    pixels = np.asarray(image, dtype=np.float32)
    # Normalización 0..1, la misma que usa la app (lo indica areas.json).
    pixels = pixels / 255.0
    interpreter.set_tensor(input_detail["index"], pixels[None, ...])
    interpreter.invoke()
    vector = interpreter.get_tensor(output_detail["index"])[0].astype(np.float32)
    norm = float(np.linalg.norm(vector))
    if norm > 0:
        vector = vector / norm
    return vector


def main() -> None:
    project = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description="Genera el índice de áreas del CECAPI.")
    parser.add_argument(
        "--refs",
        type=Path,
        default=project / "Referencias_CECAPI",
        help="Carpeta con las subcarpetas de áreas (no se modifica).",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=project / "app/src/main/assets/cecapi_areas",
        help="Carpeta de salida dentro de assets.",
    )
    parser.add_argument(
        "--model",
        type=Path,
        default=None,
        help="Modelo TFLite. Por defecto usa el que está en assets.",
    )
    args = parser.parse_args()

    refs = args.refs.resolve()
    out = args.out.resolve()
    model = (args.model or (out / "embedder.tflite")).resolve()

    if not refs.is_dir():
        raise SystemExit(f"No existe la carpeta de referencias: {refs}")
    if not model.is_file():
        raise SystemExit(f"No existe el modelo: {model}")

    areas = []
    for folder in sorted([p for p in refs.iterdir() if p.is_dir()], key=lambda p: p.name.lower()):
        files = sorted(
            p for p in folder.rglob("*") if p.is_file() and p.suffix.lower() in EXTENSIONS
        )
        if not files:
            continue
        areas.append((folder.name, files))

    if not areas:
        raise SystemExit("No se encontraron imágenes de referencia.")

    interpreter = tflite.Interpreter(model_path=str(model))
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]

    if tuple(input_detail["shape"]) != (1, SIZE, SIZE, 3):
        raise SystemExit(f"Entrada inesperada del modelo: {input_detail['shape']}")
    if len(output_detail["shape"]) != 2:
        raise SystemExit(f"Salida inesperada del modelo: {output_detail['shape']}")

    dim = int(output_detail["shape"][1])
    vectors = []
    area_indices = []
    metadata_areas = []

    print(f"Modelo: {model}")
    print(f"Referencias: {refs}")
    print(f"Dimensión de embeddings: {dim}")

    for area_index, (name, files) in enumerate(areas):
        phrase = FRASES.get(name)
        if phrase is None:
            print(f"Área sin frase configurada, se omite: {name}")
            continue
        offset = len(vectors)
        for file in files:
            vectors.append(embed(interpreter, input_detail, output_detail, file))
            area_indices.append(area_index)
        metadata_areas.append(
            {
                "id": name,
                "frase": f"Te encuentras en {phrase} del CECAPI.",
                "offset": offset,
                "count": len(files),
            }
        )
        print(f"  {name}: {len(files)} referencias")

    # Reasigna índices si se omitió algún área sin frase.
    area_index_by_name = {area["id"]: i for i, area in enumerate(metadata_areas)}
    remapped = []
    cursor = 0
    for area in metadata_areas:
        area["offset"] = cursor
        cursor += area["count"]
        remapped.extend([area_index_by_name[area["id"]]] * area["count"])

    matrix = np.asarray(vectors, dtype="<f4")
    indices = np.asarray(remapped, dtype="<i4")

    out.mkdir(parents=True, exist_ok=True)
    header = struct.pack(
        "<4s4i2f2i",
        MAGIC,
        VERSION,
        dim,
        len(matrix),
        len(metadata_areas),
        THRESHOLD,
        MARGIN,
        TOP_K,
        NORMALIZATION_CODE,
    )
    with (out / "embeddings.bin").open("wb") as stream:
        stream.write(header)
        stream.write(matrix.tobytes(order="C"))
        stream.write(indices.tobytes(order="C"))

    metadata = {
        "version": VERSION,
        "model": model.name,
        "modelSha256": sha256(model),
        "inputSize": SIZE,
        "normalization": NORMALIZATION,
        "threshold": THRESHOLD,
        "margin": MARGIN,
        "topK": TOP_K,
        "areas": metadata_areas,
    }
    (out / "areas.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    print(f"Listo: {len(matrix)} vectores, {len(metadata_areas)} áreas.")
    print(f"  {out / 'areas.json'}")
    print(f"  {out / 'embeddings.bin'}")


if __name__ == "__main__":
    main()
