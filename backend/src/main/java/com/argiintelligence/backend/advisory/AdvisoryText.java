package com.argiintelligence.backend.advisory;

import java.util.List;

/** What an {@link AdvisoryGenerator} produces: wording only, never new facts. */
public record AdvisoryText(String explanation, List<String> keyFactors, List<String> recommendedActions,
                           List<String> uncertainty) {
}
