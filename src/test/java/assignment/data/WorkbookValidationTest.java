package assignment.data;

import java.nio.file.Path;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Infrastructure checks only; these do not execute the five browser scenarios. */
public class WorkbookValidationTest {
    private Path workbookPath() {
        return Path.of(System.getProperty("workbook"));
    }

    @Test
    public void suppliedWorkbookHasValidReferences() throws Exception {
        var data = WorkbookData.load(workbookPath());
        Assert.assertFalse(data.scenarios().isEmpty());
        System.out.printf("Validated %d scenario definitions and %d steps. Browser scenarios not executed.%n",
            data.scenarios().size(), data.rows("Steps").size());
    }

    @Test
    public void missingInputReferenceIsRejected() throws Exception {
        try (var book = WorkbookFactory.create(workbookPath().toFile(), null, true)) {
            // Remove a referenced input; it must never silently resolve to a default.
            var sheet = book.getSheet("Data");
            sheet.removeRow(sheet.getRow(1));
            var error = Assert.expectThrows(IllegalArgumentException.class, () -> WorkbookData.read(book));
            Assert.assertTrue(error.getMessage().contains("Unknown input_key reference"));
        }
    }

    @Test
    public void duplicateScenarioIsRejected() throws Exception {
        try (var book = WorkbookFactory.create(workbookPath().toFile(), null, true)) {
            var sheet = book.getSheet("Scenarios");
            sheet.getRow(2).getCell(0).setCellValue(sheet.getRow(1).getCell(0).getStringCellValue());
            var error = Assert.expectThrows(IllegalArgumentException.class, () -> WorkbookData.read(book));
            Assert.assertTrue(error.getMessage().contains("Duplicate Scenarios key"));
        }
    }

    @Test
    public void duplicateStepIsRejected() throws Exception {
        try (var book = WorkbookFactory.create(workbookPath().toFile(), null, true)) {
            var sheet = book.getSheet("Steps");
            sheet.getRow(2).getCell(1).setCellValue(sheet.getRow(1).getCell(1).getNumericCellValue());
            var error = Assert.expectThrows(IllegalArgumentException.class, () -> WorkbookData.read(book));
            Assert.assertTrue(error.getMessage().contains("Duplicate step_order"));
        }
    }
}
