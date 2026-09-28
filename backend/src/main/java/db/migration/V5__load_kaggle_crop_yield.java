package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Loads the real Kaggle crop_yield.csv (the ML supply model's training data) into production_history.
 *
 * <p>The file is verified by SHA-256 before anything is inserted, so a changed file cannot be loaded under
 * this version. Every value comes from the file: text is only trimmed of padding, numbers are parsed exactly,
 * and nothing is derived or filled in. A row that breaks the table's rules fails the migration instead of
 * being skipped. The only rows left out are crops whose Production is not in tonnes (see README next to the file).
 */
public class V5__load_kaggle_crop_yield extends BaseJavaMigration {

    static final String RESOURCE = "data/kaggle-crop-yield/crop_yield.csv";
    static final String SHA256 = "ab9bc356b1f8107d490376cec24450a0e2906322ad25788ab22fb32537ab1f8f";
    static final String DATASET_VERSION = "crop_yield-sha256-ab9bc356b1f8";
    /** Production is a count of nuts (Coconut) or possibly bales (cotton), so it cannot go in production_tonnes. */
    static final Set<String> EXCLUDED_CROPS = Set.of("Coconut", "Cotton(lint)");
    static final List<String> HEADER = List.of("Crop", "Crop_Year", "Season", "State", "Area", "Production",
            "Annual_Rainfall", "Fertilizer", "Pesticide", "Yield");

    private static final String INSERT = """
            INSERT INTO production_history (id, state, crop, season, crop_year, area_hectares, production_tonnes,
                                            source, data_classification, dataset_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'KAGGLE_CROP_YIELD', 'OBSERVED', ?)""";

    record Row(String state, String crop, String season, int cropYear, BigDecimal areaHectares,
               BigDecimal productionTonnes) {
    }

    record Parsed(List<Row> rows, int rawRows, int excludedRows) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        Parsed parsed = parse(verified(readResource()));
        try (PreparedStatement insert = context.getConnection().prepareStatement(INSERT)) {
            int pending = 0;
            for (Row row : parsed.rows()) {
                insert.setObject(1, UUID.randomUUID());
                insert.setString(2, row.state());
                insert.setString(3, row.crop());
                insert.setString(4, row.season());
                insert.setInt(5, row.cropYear());
                insert.setBigDecimal(6, row.areaHectares());
                insert.setBigDecimal(7, row.productionTonnes());
                insert.setString(8, DATASET_VERSION);
                insert.addBatch();
                if (++pending == 1000) {
                    insert.executeBatch();
                    pending = 0;
                }
            }
            insert.executeBatch();
        }
    }

    static byte[] readResource() throws IOException {
        try (InputStream in = V5__load_kaggle_crop_yield.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Classpath resource " + RESOURCE + " is missing");
            }
            return in.readAllBytes();
        }
    }

    static byte[] verified(byte[] bytes) throws NoSuchAlgorithmException {
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!SHA256.equals(actual)) {
            throw new IllegalStateException(RESOURCE + " has SHA-256 " + actual + ", expected " + SHA256);
        }
        return bytes;
    }

    static Parsed parse(byte[] bytes) {
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\r?\n");
        if (!List.of(lines[0].split(",", -1)).equals(HEADER)) {
            throw new IllegalStateException("Unexpected header: " + lines[0]);
        }
        List<Row> rows = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        int raw = 0;
        int excluded = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            raw++;
            String[] f = lines[i].split(",", -1);
            if (f.length != HEADER.size()) {
                throw new IllegalStateException("Line " + (i + 1) + " has " + f.length + " fields");
            }
            String crop = f[0].strip();
            if (EXCLUDED_CROPS.contains(crop)) {
                excluded++;
                continue;
            }
            Row row = new Row(f[3].strip(), crop, f[2].strip(), Integer.parseInt(f[1].strip()),
                    new BigDecimal(f[4].strip()), new BigDecimal(f[5].strip()));
            if (row.areaHectares().signum() <= 0 || row.productionTonnes().signum() < 0) {
                throw new IllegalStateException("Line " + (i + 1) + " has non-positive area or negative production");
            }
            String key = (row.state() + "|" + row.crop() + "|" + row.season() + "|" + row.cropYear())
                    .toLowerCase(Locale.ROOT);
            if (!keys.add(key)) {
                throw new IllegalStateException("Line " + (i + 1) + " repeats state/crop/season/year " + key);
            }
            rows.add(row);
        }
        return new Parsed(List.copyOf(rows), raw, excluded);
    }
}
