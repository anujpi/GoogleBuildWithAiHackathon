package com.argiintelligence.backend.advisory;

/**
 * Turns structured, already-decided evidence into farmer-friendly text. Implementations explain; they must not add
 * facts. Keeping this behind an interface lets the LLM provider change without touching the orchestration.
 */
public interface AdvisoryGenerator {

    /** Stable id shown to the user, e.g. "GEMINI". */
    String id();

    /** Model or template version that produced the text. */
    String model();

    boolean available();

    /** @param evidenceJson the exact evidence object; @param language "en" or "hi" */
    AdvisoryText generate(String evidenceJson, String language);
}
