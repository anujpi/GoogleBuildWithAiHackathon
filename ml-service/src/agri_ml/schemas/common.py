from enum import StrEnum


class DataClassification(StrEnum):
    """How a value was obtained. Mirrors the classifications in CLAUDE.md.

    Open question: the backend also uses REGIONAL_ESTIMATE; alignment with ESTIMATED
    is tracked in ML_STATE.md and must be agreed before the first prediction contract.
    """

    OBSERVED = "OBSERVED"
    FORECAST = "FORECAST"
    MODEL_PREDICTION = "MODEL_PREDICTION"
    ESTIMATED = "ESTIMATED"
    SYNTHETIC = "SYNTHETIC"
