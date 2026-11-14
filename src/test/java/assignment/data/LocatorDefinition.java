package assignment.data;

import org.openqa.selenium.By;

public record LocatorDefinition(String strategy, String selector) {
    public By toBy() {
        return toBy(selector);
    }

    public By toBy(String resolvedSelector) {
        return switch (strategy) {
            case "css" -> By.cssSelector(resolvedSelector);
            case "id" -> By.id(resolvedSelector);
            case "xpath" -> By.xpath(resolvedSelector);
            case "name" -> By.name(resolvedSelector);
            default -> throw new IllegalArgumentException("Unsupported locator strategy: " + strategy);
        };
    }
}
