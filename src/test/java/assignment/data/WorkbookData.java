package assignment.data;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Reads workbook records without embedding scenario inputs in Java. */
public final class WorkbookData {
    private static final Map<String, List<String>> SCHEMA = Map.of(
        "Scenarios", List.of("scenario_id", "execution_order", "scenario_name", "intended_outcome", "description"),
        "Steps", List.of("scenario_id", "step_order", "action", "locator_key", "input_key", "expected_key", "timeout_seconds"),
        "Data", List.of("key", "value", "purpose"),
        "Locators", List.of("key", "strategy", "selector", "purpose"),
        "DemoData", List.of("dataset_id", "title", "description", "download_filename", "download_allowed")
    );
    private final Map<String, List<Map<String, String>>> sheets;

    private WorkbookData(Map<String, List<Map<String, String>>> sheets) {
        this.sheets = Map.copyOf(sheets);
        validate();
    }

    public static WorkbookData load(Path path) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(path.toFile(), null, true)) {
            return read(workbook);
        }
    }

    static WorkbookData read(Workbook workbook) {
        var formatter = new DataFormatter(Locale.ROOT);
        var result = new LinkedHashMap<String, List<Map<String, String>>>();
        for (var entry : SCHEMA.entrySet()) {
            String name = entry.getKey();
            var sheet = workbook.getSheet(name);
            require(sheet != null, "Missing sheet: " + name);
            Row header = sheet.getRow(0);
            require(header != null, "Missing header: " + name);
            List<String> columns = entry.getValue();
            for (int c = 0; c < columns.size(); c++) {
                require(columns.get(c).equals(formatter.formatCellValue(header.getCell(c))),
                    name + ": column " + (c + 1) + " must be " + columns.get(c));
            }
            var records = new ArrayList<Map<String, String>>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                var values = new LinkedHashMap<String, String>();
                for (int c = 0; c < columns.size(); c++) {
                    var cell = row.getCell(c);
                    require(cell == null || (cell.getCellType() != CellType.FORMULA
                        && cell.getCellType() != CellType.ERROR),
                        name + ": row " + (r + 1) + " requires literal values, not formulas or errors");
                    values.put(columns.get(c), formatter.formatCellValue(cell));
                }
                if (values.values().stream().anyMatch(v -> !v.isBlank())) records.add(Map.copyOf(values));
            }
            require(!records.isEmpty(), "Empty sheet: " + name);
            result.put(name, List.copyOf(records));
        }
        return new WorkbookData(result);
    }

    public List<Map<String, String>> rows(String sheet) {
        if (!sheets.containsKey(sheet)) throw new IllegalArgumentException("Unknown sheet: " + sheet);
        return sheets.get(sheet);
    }

    public List<Map<String, String>> scenarios() {
        return rows("Scenarios").stream()
            .sorted(Comparator.comparingInt(row -> positiveInt(row, "execution_order"))).toList();
    }

    private void validate() {
        Set<String> scenarios = uniqueKeys("Scenarios", "scenario_id");
        Set<String> data = uniqueKeys("Data", "key");
        Set<String> locators = uniqueKeys("Locators", "key");
        uniqueKeys("DemoData", "dataset_id");
        Set<Integer> orders = new HashSet<>();
        for (var row : rows("Scenarios")) {
            required(row, "scenario_name");
            require(Set.of("PASS", "FAIL").contains(row.get("intended_outcome")), "Invalid intended_outcome");
            require(orders.add(positiveInt(row, "execution_order")), "Duplicate scenario execution_order");
        }
        for (var row : rows("Data")) required(row, "value");
        for (var row : rows("Locators")) {
            required(row, "selector");
            require(Set.of("css", "id", "xpath", "name").contains(row.get("strategy")), "Invalid locator strategy");
        }
        for (var row : rows("DemoData")) {
            for (String column : List.of("title", "description", "download_filename")) required(row, column);
            require(Set.of("TRUE", "FALSE").contains(row.get("download_allowed").toUpperCase(Locale.ROOT)),
                "download_allowed must be an Excel boolean or TRUE/FALSE");
        }
        var steps = new HashSet<String>();
        var covered = new HashSet<String>();
        for (var row : rows("Steps")) {
            String id = row.get("scenario_id");
            require(scenarios.contains(id), "Unknown scenario reference: " + id);
            covered.add(id);
            require(steps.add(id + ":" + positiveInt(row, "step_order")), "Duplicate step_order in " + id);
            positiveInt(row, "timeout_seconds");
            reference(row, "locator_key", locators);
            reference(row, "input_key", data);
            reference(row, "expected_key", data);
            switch (row.get("action")) {
                case "OPEN" -> required(row, "input_key");
                case "TYPE" -> { required(row, "locator_key"); required(row, "input_key"); }
                case "CLICK" -> required(row, "locator_key");
                case "ASSERT_TEXT", "VERIFY_TEXT", "ASSERT_DOWNLOAD" -> {
                    required(row, "locator_key"); required(row, "expected_key");
                }
                default -> throw new IllegalArgumentException("Unsupported action: " + row.get("action"));
            }
        }
        require(covered.equals(scenarios), "Every scenario must have steps");
    }

    private Set<String> uniqueKeys(String sheet, String column) {
        var keys = new HashSet<String>();
        for (var row : rows(sheet)) {
            required(row, column);
            require(keys.add(row.get(column)), "Duplicate " + sheet + " key: " + row.get(column));
        }
        return keys;
    }

    private static void reference(Map<String, String> row, String column, Set<String> keys) {
        String value = row.get(column);
        require(value.isEmpty() || keys.contains(value), "Unknown " + column + " reference: " + value);
    }

    private static void required(Map<String, String> row, String column) {
        require(!row.get(column).isBlank(), "Missing required " + column);
    }

    private static int positiveInt(Map<String, String> row, String column) {
        try {
            int value = Integer.parseInt(row.get(column));
            require(value > 0, column + " must be positive");
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(column + " must be a positive integer", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
