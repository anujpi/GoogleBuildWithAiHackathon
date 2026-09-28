package db.migration;

import db.migration.V5__load_kaggle_crop_yield.Parsed;
import db.migration.V5__load_kaggle_crop_yield.Row;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the loader's parsing against the real bundled crop_yield.csv (no database needed). */
class V5LoadKaggleCropYieldTest {

    private static final String HEADER = String.join(",", V5__load_kaggle_crop_yield.HEADER);
    private static Parsed real;

    @BeforeAll
    static void parseRealFile() throws Exception {
        real = V5__load_kaggle_crop_yield.parse(
                V5__load_kaggle_crop_yield.verified(V5__load_kaggle_crop_yield.readResource()));
    }

    @Test
    void realFileMatchesTheMlDatasetVersionAndCounts() {
        // Same file as the ML model's datasetVersion crop_yield-sha256-ab9bc356b1f8 (19,689 data rows).
        assertThat(real.rawRows()).isEqualTo(19_689);
        assertThat(real.excludedRows()).isEqualTo(648); // Coconut + Cotton(lint): production not in tonnes
        assertThat(real.rows()).hasSize(19_689 - 648);
        assertThat(real.rows()).extracting(Row::crop).doesNotContain("Coconut", "Cotton(lint)");
    }

    @Test
    void valuesAreCopiedExactlyWithOnlyPaddingTrimmed() {
        Row upWheat2018 = real.rows().stream()
                .filter(r -> r.state().equals("Uttar Pradesh") && r.crop().equals("Wheat")
                        && r.season().equals("Rabi") && r.cropYear() == 2018)
                .findFirst().orElseThrow();
        assertThat(upWheat2018.areaHectares()).isEqualByComparingTo("9855900");
        assertThat(upWheat2018.productionTonnes()).isEqualByComparingTo("38039724");
        assertThat(real.rows()).extracting(Row::season)
                .containsOnly("Kharif", "Rabi", "Whole Year", "Summer", "Autumn", "Winter");
        // The dataset's own spelling is kept (two spaces), because the ML model expects it.
        assertThat(real.rows()).extracting(Row::crop).contains("Other  Rabi pulses");
    }

    @Test
    void changedFileIsRefused() {
        byte[] altered = "Crop,Crop_Year\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> V5__load_kaggle_crop_yield.verified(altered))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SHA-256");
    }

    // The inline rows below are parser test inputs only, not data that is loaded anywhere.

    @Test
    void unexpectedHeaderIsRefused() {
        assertThatThrownBy(() -> parse("Crop,Year\nWheat,2000\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("header");
    }

    @Test
    void invalidRowsFailInsteadOfBeingSkipped() {
        assertThatThrownBy(() -> parse(HEADER + "\nWheat,2000,Rabi,S,0,5,1,1,1,1\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non-positive area");
        assertThatThrownBy(() -> parse(HEADER + "\nWheat,2000,Rabi,S,1,5,1,1,1\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fields");
        assertThatThrownBy(() -> parse(HEADER + "\nWheat,2000,Rabi,S,1,5,1,1,1,1\nwheat,2000,Rabi ,s,2,6,1,1,1,1\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("repeats");
    }

    @Test
    void parsesNumbersExactly() {
        Parsed p = parse(HEADER + "\nArecanut ,1997,Whole Year ,Assam,73814.5,56708,1,1,1,1\n");
        assertThat(p.rows()).containsExactly(new Row("Assam", "Arecanut", "Whole Year", 1997,
                new BigDecimal("73814.5"), new BigDecimal("56708")));
    }

    private static Parsed parse(String csv) {
        return V5__load_kaggle_crop_yield.parse(csv.getBytes(StandardCharsets.UTF_8));
    }
}
