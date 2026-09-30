package com.argiintelligence.backend.advisory;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic fallback used when Gemini is not configured or fails. It fills fixed English/Hindi sentences with
 * values copied from the evidence, so it can never add a fact. It is always labelled TEMPLATE_FALLBACK, not AI.
 */
@Component
public class TemplateAdvisoryGenerator implements AdvisoryGenerator {

    private static final Map<String, String> STATUS_HI = Map.of(
            "SUITABLE_CANDIDATE", "यह फसल इस खेत के लिए उपयुक्त विकल्प है",
            "SUITABLE_WITH_MARKET_RISK", "फसल उपयुक्त है, लेकिन क्षेत्र में आपूर्ति माँग से काफ़ी अधिक है",
            "NOT_RECOMMENDED", "उपलब्ध प्रमाण इस फसल का कमज़ोर समर्थन करते हैं",
            "REVIEW", "यह फसल संभव है; निर्णय से पहले जोखिम देखें",
            "INSUFFICIENT_EVIDENCE", "इस फसल का आकलन करने के लिए पर्याप्त डेटा नहीं है");
    private static final Map<String, String> LEVEL_HI = Map.of("HIGH", "उच्च", "MODERATE", "मध्यम", "LOW", "कम",
            "UNKNOWN", "अज्ञात");
    private static final Map<String, String> GAP_HI = Map.of("SURPLUS", "अधिशेष (आपूर्ति अधिक)",
            "DEFICIT", "कमी (माँग अधिक)", "BALANCED", "संतुलित");

    private final JsonMapper jsonMapper;

    public TemplateAdvisoryGenerator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String id() {
        return "TEMPLATE_FALLBACK";
    }

    @Override
    public String model() {
        return "advisory-template-v1";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public AdvisoryText generate(String evidenceJson, String language) {
        JsonNode e = jsonMapper.readTree(evidenceJson);
        boolean hi = "hi".equals(language);
        JsonNode farm = e.get("farm");
        JsonNode decision = e.get("decision");
        String crop = text(e, "focusCrop", hi ? "चयनित फसल" : "the selected crop");
        String status = text(decision, "status", "INSUFFICIENT_EVIDENCE");

        StringBuilder ex = new StringBuilder();
        if (hi) {
            ex.append(STATUS_HI.getOrDefault(status, status)).append("। ")
                    .append(text(farm, "name", "खेत")).append(" (").append(text(farm, "district", "")).append(", ")
                    .append(text(farm, "state", "")).append(") के लिए ").append(crop).append(" का आकलन किया गया। ");
        } else {
            ex.append(text(decision, "headline", "Assessment complete")).append(". For ")
                    .append(text(farm, "name", "this farm")).append(" in ").append(text(farm, "district", ""))
                    .append(", ").append(text(farm, "state", "")).append(", the platform assessed ").append(crop)
                    .append(". ");
        }
        JsonNode cand = e.path("suitability").path("focusCandidate");
        if (cand.isObject()) {
            String score = cand.path("suitabilityScore").asString();
            ex.append(hi ? "उपयुक्तता सूचकांक " + score + " है (यह संभावना नहीं, तुलना का पैमाना है)। "
                    : "Its suitability index is " + score + " (a ranking index, not a probability). ");
        }
        JsonNode gap = e.path("gap");
        if ("OK".equals(gap.path("status").asString())) {
            JsonNode g = gap.path("data");
            String st = g.path("status").asString();
            ex.append(hi ? g.path("year").asString() + " के आँकड़ों में राज्य की आपूर्ति माँग के अनुमान की तुलना में "
                    + GAP_HI.getOrDefault(st, st) + " है (" + g.path("gapPctOfDemand").asString() + "%)। "
                    : "In " + g.path("year").asString() + ", state supply was " + st.toLowerCase(Locale.ROOT)
                    + " against the demand proxy (" + g.path("gapPctOfDemand").asString() + "%). ");
        }
        JsonNode w = e.path("weather");
        if ("OK".equals(w.path("status").asString())) {
            JsonNode n = w.path("next7Days");
            ex.append(hi ? "अगले 7 दिनों में लगभग " + n.path("totalPrecipitationMm").asString()
                    + " मिमी वर्षा का पूर्वानुमान है। "
                    : "About " + n.path("totalPrecipitationMm").asString()
                    + " mm of rain is forecast over the next 7 days. ");
        } else {
            ex.append(hi ? "मौसम का लाइव डेटा उपलब्ध नहीं है। " : "Live weather data is unavailable. ");
        }
        JsonNode d = e.path("disease");
        if (d.isObject()) {
            ex.append(hi ? "पत्ती की फ़ोटो का मॉडल परिणाम: " + d.path("crop").asString() + " – "
                    + d.path("disease").asString() + " (संभावना " + d.path("probability").asString() + ")। "
                    : "The leaf photo was classified as " + d.path("crop").asString() + " – "
                    + d.path("disease").asString() + " (model probability " + d.path("probability").asString()
                    + "). ");
        }

        List<String> factors = new ArrayList<>();
        decision.path("reasons").forEach(r -> factors.add(r.asString()));
        e.path("risk").path("factors").forEach(f -> {
            if (!"LOW".equals(f.path("level").asString())) {
                String lvl = f.path("level").asString();
                factors.add((hi ? LEVEL_HI.getOrDefault(lvl, lvl) + " जोखिम – " : lvl + " risk – ")
                        + f.path("category").asString() + ": " + f.path("reason").asString());
            }
        });

        List<String> actions = new ArrayList<>();
        e.path("risk").path("factors").forEach(f -> {
            String cat = f.path("category").asString();
            String lvl = f.path("level").asString();
            if (cat.equals("WEATHER_RAINFALL") && !lvl.equals("LOW")) {
                actions.add(hi ? "पूर्वानुमानित वर्षा को देखते हुए खेत की जल-निकासी और कटाई का समय तय करें।"
                        : "Plan field drainage and harvest timing around the forecast rain.");
            }
            if (cat.equals("DISEASE_CONDITIONS") && !lvl.equals("LOW")) {
                actions.add(hi ? "नमी अधिक है: पत्तियों पर धब्बों की नियमित जाँच करें।"
                        : "Humidity is high: scout leaves regularly for spots or blight.");
            }
            if (cat.equals("MARKET_OVERSUPPLY") && lvl.equals("HIGH")) {
                actions.add(hi ? "क्षेत्र में आपूर्ति अधिक है: वैकल्पिक उपयुक्त फसलों की तुलना करें।"
                        : "Regional supply is high: compare the suitable alternative crops.");
            }
        });
        if (d.isObject()) {
            actions.add(hi ? "किसी भी उपचार से पहले निदान की पुष्टि कृषि विज्ञान केंद्र (KVK) से कराएँ।"
                    : "Confirm the diagnosis with your local KVK/agronomist before any treatment.");
        }
        actions.add(hi ? "बुवाई या बिक्री से पहले स्थानीय मंडी भाव देखें – मूल्य डेटा इसमें शामिल नहीं है।"
                : "Check local mandi prices before planting or selling – price data is not included here.");

        List<String> uncertainty = new ArrayList<>();
        e.path("dataNotices").forEach(n -> uncertainty.add(n.asString()));
        if (hi) {
            uncertainty.addFirst("यह स्वचालित टेम्पलेट सारांश है (AI उपलब्ध नहीं था); नीचे दी गई सीमाएँ अंग्रेज़ी में हैं।");
        }
        return new AdvisoryText(ex.toString().strip(), factors, actions, uncertainty);
    }

    private static String text(JsonNode n, String field, String fallback) {
        JsonNode v = n == null ? null : n.get(field);
        return v == null || v.isNull() ? fallback : v.asString();
    }
}
