"""Load a saved leaf-disease classifier and return class probabilities for one image.

The architecture and preprocessing are rebuilt from metadata.json, so training and serving
share one definition. Loading never downloads pretrained weights: the saved state dict holds
all parameters.
"""

from __future__ import annotations

import io
import json
from dataclasses import dataclass
from pathlib import Path

# torch and xgboost each bundle their own libomp on macOS, and two OpenMP runtimes in one process
# break the supply endpoint. Measured on this machine: importing torch before xgboost makes
# Booster.load_model() segfault; with xgboost first, a multi-threaded torch forward pass still
# segfaults or deadlocks later xgboost calls. Importing xgboost first AND running torch with one
# intra-op thread (DiseaseModel sets it) passed 20 interleaved torch/xgboost rounds. isort: off
try:
    import xgboost  # noqa: F401
except ImportError:  # the ml extra is optional; nothing to protect without it
    pass
import torch  # isort: on
from PIL import Image, UnidentifiedImageError
from torch import nn
from torchvision import models, transforms

ARCHITECTURE = "mobilenet_v3_large"
# ImageNet preprocessing of torchvision's MobileNet_V3_Large_Weights.IMAGENET1K_V2.
PREPROCESSING = {
    "resize": 232,
    "centerCrop": 224,
    "interpolation": "bilinear",
    "mean": [0.485, 0.456, 0.406],
    "std": [0.229, 0.224, 0.225],
    "colorMode": "RGB",
}
MAX_IMAGE_PIXELS = 40_000_000


class ModelLoadError(RuntimeError):
    pass


class InvalidImageError(ValueError):
    pass


def build_network(num_classes: int, pretrained: bool = False) -> nn.Module:
    weights = models.MobileNet_V3_Large_Weights.IMAGENET1K_V2 if pretrained else None
    net = models.mobilenet_v3_large(weights=weights)
    head = net.classifier[-1]
    net.classifier[-1] = nn.Linear(head.in_features, num_classes)
    return net


def eval_transform(p: dict = PREPROCESSING) -> transforms.Compose:
    return transforms.Compose([
        transforms.Resize(p["resize"], interpolation=transforms.InterpolationMode.BILINEAR),
        transforms.CenterCrop(p["centerCrop"]),
        transforms.ToTensor(),
        transforms.Normalize(p["mean"], p["std"]),
    ])


def decode_image(data: bytes, expected_format: str | None = None) -> Image.Image:
    """Decode uploaded bytes into an RGB image, rejecting empty, corrupt or huge files.

    expected_format (e.g. "JPEG") rejects files whose content does not match their declared type.
    """
    if not data:
        raise InvalidImageError("image is empty")
    try:
        with Image.open(io.BytesIO(data)) as probe:
            if expected_format and probe.format != expected_format:
                raise InvalidImageError(
                    f"file content is {probe.format}, not the declared {expected_format}")
            probe.verify()
        im = Image.open(io.BytesIO(data))
        if im.width * im.height > MAX_IMAGE_PIXELS:
            raise InvalidImageError(f"image has more than {MAX_IMAGE_PIXELS} pixels")
        im.load()
    except InvalidImageError:
        raise
    except (UnidentifiedImageError, OSError, SyntaxError, ValueError,
            Image.DecompressionBombError) as exc:
        raise InvalidImageError(f"could not decode image: {exc}") from exc
    return im.convert("RGB")


@dataclass(frozen=True)
class ClassProbability:
    label: str
    crop: str
    disease: str
    probability: float


class DiseaseModel:
    def __init__(self, network: nn.Module, metadata: dict):
        # Single-threaded CPU inference; see the libomp note at the top. ~24 ms per image here.
        torch.set_num_threads(1)
        self.network = network.eval()
        self.metadata = metadata
        self.classes: list[dict] = metadata["classes"]
        self.transform = eval_transform(metadata["preprocessing"])

    @classmethod
    def load(cls, model_dir: Path) -> DiseaseModel:
        # Must happen before any tensor work: with xgboost's libomp loaded, a multi-threaded
        # load_state_dict segfaults (measured 2026-09-30), not only the forward pass.
        torch.set_num_threads(1)
        model_path, meta_path = model_dir / "model.pt", model_dir / "metadata.json"
        if not model_path.is_file() or not meta_path.is_file():
            raise ModelLoadError(f"disease model artifact not found in {model_dir}")
        try:
            metadata = json.loads(meta_path.read_text())
            if metadata["architecture"] != ARCHITECTURE:
                raise ModelLoadError(f"unsupported architecture {metadata['architecture']!r}")
            classes = metadata["classes"]
            if [c["index"] for c in classes] != list(range(len(classes))):
                raise ModelLoadError("class mapping indices are not 0..n-1 in order")
            net = build_network(len(classes))
            state = torch.load(model_path, map_location="cpu", weights_only=True)
            net.load_state_dict(state)
        except ModelLoadError:
            raise
        except Exception as exc:
            raise ModelLoadError(f"could not load disease model from {model_dir}: {exc}") from exc
        return cls(net, metadata)

    @property
    def version(self) -> str:
        return self.metadata["modelVersion"]

    @torch.inference_mode()
    def predict(self, image: Image.Image) -> list[ClassProbability]:
        """Softmax probabilities for every class, highest first."""
        x = self.transform(image.convert("RGB")).unsqueeze(0)
        probs = torch.softmax(self.network(x), dim=1)[0].tolist()
        ranked = sorted(zip(self.classes, probs, strict=True), key=lambda t: -t[1])
        return [ClassProbability(c["label"], c["crop"], c["disease"], float(p))
                for c, p in ranked]

    def predict_bytes(self, data: bytes) -> list[ClassProbability]:
        return self.predict(decode_image(data))
