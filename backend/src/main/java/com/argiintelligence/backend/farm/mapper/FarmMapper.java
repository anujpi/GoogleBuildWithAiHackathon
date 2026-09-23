package com.argiintelligence.backend.farm.mapper;

import com.argiintelligence.backend.farm.dto.FarmRequest;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.entity.Farm;
import com.argiintelligence.backend.farm.entity.FarmLocation;
import com.argiintelligence.backend.farm.entity.SoilProfile;

public final class FarmMapper {

    private FarmMapper() {
    }

    /** Copies every request field onto the farm, creating location/soil on first use and updating them in place after. */
    public static void apply(FarmRequest req, Farm farm) {
        farm.setName(req.name().strip());
        farm.setArea(req.area());
        farm.setAreaUnit(req.areaUnit());
        farm.setIrrigationType(req.irrigationType());
        farm.setCurrentCrop(req.currentCrop());
        farm.setPreviousCrop(req.previousCrop());
        farm.setSeason(req.season());

        FarmLocation location = farm.getLocation() != null ? farm.getLocation() : new FarmLocation();
        FarmRequest.Location l = req.location();
        location.setLatitude(l.latitude());
        location.setLongitude(l.longitude());
        location.setState(l.state().strip());
        location.setDistrict(l.district().strip());
        location.setTaluk(l.taluk());
        location.setAddressLabel(l.addressLabel());
        farm.setLocation(location);

        FarmRequest.Soil s = req.soilProfile();
        if (s == null) {
            farm.setSoilProfile(null);
            return;
        }
        SoilProfile soil = farm.getSoilProfile() != null ? farm.getSoilProfile() : new SoilProfile();
        soil.setPh(s.ph());
        soil.setElectricalConductivity(s.electricalConductivity());
        soil.setOrganicCarbon(s.organicCarbon());
        soil.setNitrogen(s.nitrogen());
        soil.setPhosphorus(s.phosphorus());
        soil.setPotassium(s.potassium());
        soil.setSulphur(s.sulphur());
        soil.setZinc(s.zinc());
        soil.setIron(s.iron());
        soil.setManganese(s.manganese());
        soil.setCopper(s.copper());
        soil.setBoron(s.boron());
        soil.setSource(s.source());
        soil.setDataClassification(s.dataClassification());
        soil.setMeasuredAt(s.measuredAt());
        soil.setConfidence(s.confidence());
        farm.setSoilProfile(soil);
    }

    public static FarmResponse toResponse(Farm farm) {
        FarmLocation l = farm.getLocation();
        SoilProfile s = farm.getSoilProfile();
        return new FarmResponse(
                farm.getId(),
                farm.getName(),
                farm.getArea(),
                farm.getAreaUnit(),
                farm.getIrrigationType(),
                farm.getCurrentCrop(),
                farm.getPreviousCrop(),
                farm.getSeason(),
                new FarmResponse.Location(l.getId(), l.getLatitude(), l.getLongitude(),
                        l.getState(), l.getDistrict(), l.getTaluk(), l.getAddressLabel()),
                s == null ? null : new FarmResponse.Soil(s.getId(), s.getPh(), s.getElectricalConductivity(),
                        s.getOrganicCarbon(), s.getNitrogen(), s.getPhosphorus(), s.getPotassium(), s.getSulphur(),
                        s.getZinc(), s.getIron(), s.getManganese(), s.getCopper(), s.getBoron(), s.getSource(),
                        s.getDataClassification(), s.getMeasuredAt(), s.getConfidence()),
                s != null,
                farm.getCreatedAt(),
                farm.getUpdatedAt());
    }
}
