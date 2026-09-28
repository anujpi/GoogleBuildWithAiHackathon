from typing import Literal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class HealthResponse(BaseModel):
    # camelCase on the wire to match the Spring Boot JSON conventions.
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    status: Literal["UP"]
    service: str
    version: str
    environment: str
    # No models are served yet; this list fills in as inference wrappers are added.
    loaded_models: list[str]
