# PlantVillage (color), Potato + Tomato subset

| Field | Value |
|---|---|
| Dataset name | PlantVillage, `color` configuration |
| Source | Hugging Face `mohanty/PlantVillage`, pinned revision `9e97599868962bd0079b8db4b7f1efa9185fa1e7` |
| URL | https://huggingface.co/datasets/mohanty/PlantVillage |
| Citation | Mohanty, Hughes & Salathé (2016), *Using deep learning for image-based plant disease detection*, Front. Plant Sci. 7, doi:10.3389/fpls.2016.01419 |
| License | CC BY-SA 3.0 (per the dataset card) |
| Coverage | 54,306 leaf images, 14 crops, 38 classes. **MVP uses 13 Potato and Tomato classes (20,312 images)** |
| Granularity | One RGB photo of one detached leaf, mostly on a plain background (lab / controlled imagery) |
| Target | `Crop___Disease` class label as published |
| Classification | OBSERVED (real photographs; labels from the publisher) |
| Version | `plantvillage-color-9e975998-39349c8dc33e` (the pinned revision plus a hash of the split files and leaf map) |

## Download (not committed)

These files go in `data/raw/plantvillage/`:

- `data.zip`: 2,184,723,441 bytes, sha256 `fba30c6a…45e84`. It is verified by `scripts/prepare_plantvillage.py`.
- `splits/color_{train,test}.txt`
- `leaf_grouping/leaf-map.json`
- `README.md`, `plant_village.py`

All come from the same revision, via `huggingface-cli download mohanty/PlantVillage --repo-type dataset
--revision 9e97599868962bd0079b8db4b7f1efa9185fa1e7 --local-dir data/raw/plantvillage`.

## Preprocessing and split (`pv-split-v2`, seed 42)

`python scripts/prepare_plantvillage.py` does the following:

1. Extracts only the Potato and Tomato images.
2. Decodes every image and computes a 64-bit dHash.
3. Groups images that are the same physical leaf. Two images join a group when any of these match:
   - the publisher's `leaf_id`
   - the filename stem
   - dHashes within 6 bits of each other, in the same class
4. Assigns **whole groups** to test (20 %), validation (15 %) and train, stratified per class.

The publisher's own 80/20 split isn't used, because 6,749 images only have fallback leaf ids. The result is
10,127 distinct groups, and the leakage report shows **0 shared groups, leaf ids or dHashes** between any two splits.

| Split | Images |
|---|---|
| train | 13,182 |
| val | 3,050 |
| test | 4,080 |

Per-class counts are in `data/processed/plantvillage/<version>/pv-split-v2/manifest.json`. The classes are
imbalanced: Potato___healthy has 152 images in total, while Tomato Yellow Leaf Curl Virus has 5,357.

## Known biases and limitations

- **Controlled backgrounds.** Models learn background, lighting and colour shortcuts. A colour-histogram logistic
  regression alone reaches about 0.66 test macro-F1 (see the model card). Accuracy on field photos is expected to be
  much lower, and it has not been measured.
- **Coverage.** Only Potato and Tomato are covered. Any other crop's leaf is still forced into one of the 13
  classes.
- **Healthy potato** has very few images.
