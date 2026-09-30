# Model card: leaf-disease classifier `disease-mnv3-probe-v1`

| Field | Value |
|---|---|
| Model name / version | `leaf-disease-classifier` / `disease-mnv3-probe-v1` |
| Served by | `POST /v1/predict/disease` (default `AGRI_ML_DISEASE_MODEL_DIR`) |
| Type | Transfer learning, linear probe: torchvision MobileNetV3-Large with its ImageNet (IMAGENET1K_V2) weights **frozen**. Only the final Linear(1280 → 13) layer is fitted, as a multinomial logistic regression (lbfgs, C = 0.3 chosen on validation macro-F1 from {0.1, 0.3, 1, 3}). The weights are copied into the network's last layer, so the served softmax equals the regression's `predict_proba` (checked in the script) |
| Trained | 2026-09-30T15:48:41Z, CPU, seed 42. MLflow run `1eaa8d0599454fd68af43b11a8ffee4c` (experiment `disease-classification`) |
| Script | `scripts/train_disease_probe.py`. Per-split features are cached next to the split in `data/processed/…/pv-split-v2/features-*.npy` |
| Data | PlantVillage `color`, Potato + Tomato (13 classes), `plantvillage-color-9e975998-39349c8dc33e`, split `pv-split-v2` (see `docs/datasets/plantvillage.md`) |
| Split | train 13,182 / val 3,050 / test 4,080 images. Groups are split by leaf, filename and near-duplicate hash, with 0 shared between splits. Test was scored once, after C was selected |
| Preprocessing | Resize 232 (bilinear) → centre crop 224 → ImageNet mean/std, RGB (`disease-prep-v1`). No augmentation |
| Output | Softmax over 13 classes. The top class plus the top 3 are returned with `calibrated: false` |

## Why a linear probe rather than full fine-tuning

Full fine-tuning (`scripts/train_disease.py`, all layers, Apple MPS) reached a **validation** macro-F1 of 0.985
after 2 epochs. On the 8 GB development machine, though, it stalled under memory pressure in four separate runs
and never completed, so it has **no artifact and no test result**. The linear probe is the model that actually
exists and was evaluated.

## Held-out results (PlantVillage test split, n = 4,080)

| Model | Accuracy | Macro-F1 | Weighted-F1 | ECE (15 bins) |
|---|---|---|---|---|
| Majority class (baseline) | 0.263 | 0.032 | – | – |
| Colour-histogram logistic regression (baseline, shortcut probe) | 0.722 | 0.660 | – | – |
| **disease-mnv3-probe-v1** | **0.951** | **0.942** | 0.951 | 0.018 |

On validation (n = 3,050) the probe scores accuracy 0.953, macro-F1 0.942 and ECE 0.014.

Per class (test):

| Class | Precision | Recall | F1 | n |
|---|---|---|---|---|
| Potato Early blight | 0.976 | 1.000 | 0.988 | 200 |
| Potato Late blight | 0.964 | 0.950 | 0.957 | 200 |
| Potato healthy | 1.000 | 0.938 | 0.968 | 32 |
| Tomato Bacterial spot | 0.950 | 0.967 | 0.958 | 428 |
| Tomato Early blight | 0.855 | 0.783 | **0.817** | 203 |
| Tomato Late blight | 0.930 | 0.940 | 0.935 | 383 |
| Tomato Leaf mold | 0.963 | 0.938 | 0.950 | 192 |
| Tomato Septoria leaf spot | 0.938 | 0.927 | 0.932 | 356 |
| Tomato Spider mites | 0.909 | 0.949 | 0.929 | 336 |
| Tomato Target spot | 0.876 | 0.907 | 0.892 | 281 |
| Tomato Yellow leaf curl virus | 0.993 | 0.990 | 0.992 | 1,072 |
| Tomato mosaic virus | 0.947 | 0.947 | 0.947 | 75 |
| Tomato healthy | 0.990 | 0.969 | 0.980 | 322 |

Coverage vs. accuracy on test, when only predictions at or above a threshold are kept:

| Threshold | Coverage | Accuracy on covered |
|---|---|---|
| 0.5 | 97.6 % | 96.3 % |
| 0.7 | 92.5 % | 98.2 % |
| 0.9 | 81.7 % | 99.4 % |
| 0.99 | 55.5 % | 99.9 % |

The backend flags `needsExpertReview` below 0.70. That threshold is a product policy informed by this table, and
because the table comes from in-distribution lab images, it does not transfer to field photos.

## Error analysis (`error_analysis.json`)

- 199 test errors. Only 20 of them confuse the crop (Potato vs. Tomato); the rest mix up diseases within a crop.
- Largest confusion pairs:
  - Tomato Late blight → Early blight (16)
  - Target spot ↔ Spider mites (14 each way)
  - Early blight → Target spot (14)
- Tomato Early blight is the weakest class (recall 0.78).
- Healthy Potato has only 32 test images, so its metrics are noisy.

## Limitations — read before use

- **Lab imagery.** PlantVillage photos are single leaves on plain backgrounds. The colour-histogram baseline alone
  reaches 0.66 macro-F1, which shows how much background and colour shortcuts are available. **Accuracy on real
  field photos was not measured and is expected to be substantially lower.**
- **Coverage.** It covers Potato and Tomato only, and any other leaf is still forced into one of the 13 classes. The
  backend warns when the farm's crop isn't supported.
- **Calibration.** The probability is a softmax output. ECE is low *in distribution* (0.018), but the model is not
  calibrated for field conditions, and the API reports `calibrated: false`.
- **Not a diagnosis.** A result must be confirmed by an agronomist or KVK before treatment.
