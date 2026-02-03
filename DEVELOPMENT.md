# Development Guide: Hot Reloading

It seems you are having trouble with hot reloading (changes taking effect without manual restart). Here is the recommended workflow for Spring Boot with Gradle and Kotlin.

## The Problem with `./gradlew -t bootRun`
Running `gradle -t bootRun` stops and fully restarts the application task every time you save a file. This is slow and can sometimes be flaky if the restart happens before the compilation is fully written.

## Recommended Workflow (Fastest)

The best way to use Spring Boot DevTools is to keep the application running and just **recompile** the changed files. DevTools will detect the new `.class` files and perform a "hot restart" of the Spring Context (which is much faster than a full JVM restart).

### Method 1: Using IntelliJ IDEA (Recommended)
1.  **Run the App**: Run the application normally using `./gradlew bootRun` (or the Run button in IntelliJ).
2.  **Make Changes**: Edit your `.kt` or `.html` files.
3.  **Trigger Reload**:
    - For **Kotlin/Java**: Press `Ctrl + F9` (Build Project) or go to **Build -> Build Project**. IntelliJ will recompile only the changed files. DevTools will detect this and restart the context.
    - For **HTML/CSS**: Just save the file (`Ctrl + S`). Changes should appear immediately on refresh (assuming `spring.thymeleaf.cache=false` is set, which it is).

### Method 2: Using Terminal Only
If you prefer the terminal or aren't using IntelliJ's build system:

1.  **Terminal 1 (Run App)**:
    ```bash
    ./gradlew bootRun
    ```
    *Let this run.*

2.  **Terminal 2 (Watch & Compile)**:
    ```bash
    ./gradlew -t compileKotlin compileJava processResources
    ```
    *This will watch your source files and recompile them instantly when you save. The running app in Terminal 1 will detect the changes via DevTools and reload automatically.*

## Troubleshooting
- **Browser Caching**: Disable browser cache or use Incognito mode if CSS/JS changes aren't showing.
- **Auto-Save**: Ensure your editor is actually saving the file to disk.
