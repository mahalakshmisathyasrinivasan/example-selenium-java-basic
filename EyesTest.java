package com.applitools.example;

import com.applitools.eyes.selenium.Eyes;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.selenium.ClassicRunner;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/*
    Minimal Applitools Eyes smoke test.

    Intentionally as small as possible: open -> navigate -> check -> close.
    Used to confirm the CI pipeline (TeamCity -> Maven -> Eyes SDK -> Applitools
    dashboard) works end-to-end, independent of any larger/more complex test
    class's own code paths.
*/
public class EyesTest {

    public static void main(String[] args) {
        ClassicRunner runner = new ClassicRunner();
        Eyes eyes = new Eyes(runner);

        // API key is read from the APPLITOOLS_API_KEY environment variable automatically

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        WebDriver driver = new ChromeDriver(options);

        try {
            eyes.open(driver, "TeamCity Pipeline Smoke Test", "Minimal Eyes Test", new RectangleSize(1024, 768));

            driver.get("https://applitools.com/helloworld");

            eyes.check(com.applitools.eyes.selenium.fluent.Target.window().fully());

            eyes.closeAsync();
            System.out.println("MinimalEyesTest: check submitted successfully.");
        } catch (Throwable t) {
            System.err.println("MinimalEyesTest failed: " + t);
            t.printStackTrace();
            eyes.abortAsync();
            System.exit(1);
        } finally {
            driver.quit();
            runner.getAllTestResults(false);
        }
    }
}
