package com.applitools.example;

import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.selenium.ClassicRunner;
import com.applitools.eyes.selenium.Eyes;
import com.applitools.eyes.selenium.fluent.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/*
    Minimal Applitools Eyes smoke test (JUnit 5 / Surefire).

    As small as possible: open -> navigate -> check -> close. Used to confirm
    the CI pipeline (TeamCity -> Maven -> Surefire -> Eyes SDK -> Applitools
    dashboard) works end-to-end.
*/
public class EyesTest {

    private ClassicRunner runner;
    private Eyes eyes;
    private WebDriver driver;

    @BeforeEach
    public void setUp() {
        runner = new ClassicRunner();
        eyes = new Eyes(runner);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        driver = new ChromeDriver(options);
    }

    @Test
    public void checkHelloWorldPage() {
        eyes.open(driver, "TeamCity Pipeline Smoke Test", "Minimal Eyes Test", new RectangleSize(1024, 768));
        driver.get("https://applitools.com/helloworld");
        eyes.check(Target.window().fully());
        eyes.close();
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
        if (eyes.getIsOpen()) {
            eyes.abortIfNotClosed();
        }
        runner.getAllTestResults(false);
    }
}
