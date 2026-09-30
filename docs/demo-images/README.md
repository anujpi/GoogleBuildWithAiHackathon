# Demo leaf images

Two images from the **held-out test split** of PlantVillage (Hugging Face `mohanty/PlantVillage`, revision
`9e97599868962bd0079b8db4b7f1efa9185fa1e7`, CC BY-SA 3.0; Mohanty, Hughes & Salathé 2016). They are unmodified,
and the model never saw them during training.

| File | True label | `disease-mnv3-probe-v1` output |
|---|---|---|
| `potato-late-blight-correct.jpg` | Potato Late blight | Potato Late blight, p = 0.97 |
| `potato-late-blight-misclassified.jpg` | Potato Late blight | **Tomato Early blight**, p = 0.75: one of the 199 test errors, useful for showing the model can be wrong |
