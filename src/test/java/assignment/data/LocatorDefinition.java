package assignment.data;

import org.openqa.selenium.By;

public record LocatorDefinition(String strategy, String selector) {
    public By toBy() {
        return switch (strategy) {
            case "css" -> By.cssSelector(selector);
            case "id" -> By.id(selector);
            case "xpath" -> By.xpath(selector);
            case "name" -> By.name(selector);
            default -> throw new IllegalArgumentException("Unsupported locator strategy: " + strategy);
        };
    }
}
