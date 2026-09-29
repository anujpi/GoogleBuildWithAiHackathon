from agri_ml.schemas.common import CamelModel, Season


class StateRef(CamelModel):
    state_id: str
    label: str


class DistrictRef(CamelModel):
    district_id: str
    state_id: str
    label: str


class CropRef(CamelModel):
    crop_id: str
    label: str


class SupplySeriesRef(CamelModel):
    district_id: str
    crop_id: str
    season: Season
    first_year: int
    last_year: int
    years_observed: int


class MarketSeriesRef(CamelModel):
    district_id: str
    crop_id: str
    first_month: str
    last_month: str
    months_observed: int


class DatasetRef(CamelModel):
    source: str
    dataset_version: str
    data_through: str


class ScopeResponse(CamelModel):
    states: list[StateRef]
    districts: list[DistrictRef]
    crops: list[CropRef]
    seasons: list[Season]
    supply_series: list[SupplySeriesRef]
    # Empty until a market model is trained and served (milestone M4).
    market_series: list[MarketSeriesRef]
    datasets: list[DatasetRef]
