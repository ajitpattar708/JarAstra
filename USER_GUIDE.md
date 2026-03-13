# JarAstra User Guide: Professional Remediation

Welcome to JarAstra GA. This guide provides step-by-step instructions to configure and run the AI-Native remediation engine.

---

## 1. Prerequisites

You need:

- Java 17 or newer
- Maven 3.8.5 or newer
- Ollama installed locally if you are using the default local LLM path

Supported local model examples:

- `llama3`
- `codellama:7b`

---

## 2. Install JarAstra Locally First

Before using JarAstra from another Maven project, install the JarAstra artifacts into your local Maven repository:

```bash
cd /path/to/JarAstra
mvn clean install
```

Why this matters:

- the Maven plugin depends on `com.jarastra.remediation:core-engine:1.0.0`
- external projects load the plugin from your local `~/.m2/repository`
- if you skip local installation, or if the local install is stale, plugin execution can fail before remediation starts

Expected result:

- `jarastra-maven-plugin` is installed into `~/.m2/repository/com/jarastra/remediation/jarastra-maven-plugin/1.0.0/`
- `core-engine` is installed into `~/.m2/repository/com/jarastra/remediation/core-engine/1.0.0/`

Important repository detail:

- this repo does not include checked-in `core-engine` Java sources
- during `mvn clean install`, the `core-engine` module is populated from `libs/core-engine-1.0.0.jar`
- this is required so the plugin can work from external projects

If plugin execution later fails with a missing-class error from `com.jarastra.remediation.core.*`, rerun:

```bash
cd /path/to/JarAstra
mvn clean install
```

---

## 3. Local LLM Setup (Recommended)
JarAstra is built for privacy. We recommend using **Ollama** to run reasoning models locally.

1.  **Install Ollama**: Download from [ollama.com](https://ollama.com/).
2.  **Pull a Model**: For example, run `ollama pull llama3` or `ollama pull codellama:7b`.
3.  **Configure**: Create `~/.jarastra/jarastra.properties` with:
    ```properties
    llm.provider=ollama
    llm.model=llama3
    llm.base.url=http://localhost:11434
    ```

If you want to use Code Llama instead:

```properties
llm.provider=ollama
llm.model=codellama:7b
llm.base.url=http://localhost:11434
```

### Verify the Model Really Exists
Before running JarAstra, confirm the configured model is available locally:

```bash
ollama list
```

If your configured model is missing, either:

- run `ollama pull llama3`, or
- run `ollama pull codellama:7b`, or
- change `llm.model` in `~/.jarastra/jarastra.properties` to a model that already exists on your machine

If Ollama returns:

- `404 - {"error":"model 'llama3' not found"}`

or a similar model-not-found error for `codellama:7b`, the plugin is running correctly, but your Ollama model configuration is wrong for the current machine.

### Using Cloud Providers (OpenAI)
If you prefer cloud-based analysis, use an OpenAI-compatible configuration:
```properties
llm.provider=openai
llm.openai.model=gpt-4
llm.openai.base.url=https://api.openai.com/v1
llm.api.key=sk-...
```

---

## 4. Using the CLI Tool
The CLI is the fastest way to run one-off analyses on any project folder.

### Step 1: Analysis (Safe Mode)
Runs the ASM-based reachability scan and AI reasoning without modifying your source code.
```bash
java -jar libs/core-engine-1.0.0.jar /path/to/project
```
-   **Output**: Console summary of vulnerable nodes.
-   **Verification**: Check if the reported vulnerabilities are marked as "Reachable".

### Step 2: Simulation (Dry-Run Mode)
Preview the entire remediation plan, including build file changes and AST-based source code migrations, without writing anything to disk.
```bash
java -jar libs/core-engine-1.0.0.jar /path/to/project --dry-run
```

### Step 3: Remediation (Upgrade Mode)
Instructs the engine to apply the AI-suggested version upgrades and source refactors.
```bash
java -jar libs/core-engine-1.0.0.jar /path/to/project --upgrade
```
-   **Safe Remediation**: JarAstra creates atomic backups of all modified files. If the post-upgrade build verification fails, it will **automatically roll back** the changes.
-   **AST-Refactoring**: If moving between major versions (e.g., JEE to Jakarta), JarAstra uses **JavaParser** to perform structural code migrations.
-   **Result**: Modifies your files and generates `jarastra-remediation-report.md`.

---

## 5. Maven Plugin Integration
Integrate JarAstra directly into your development lifecycle.

### Step 1: Enable Short Prefix
Add the JarAstra group to your `~/.m2/settings.xml` to use the `jarastra:` shorthand.
```xml
<settings>
  <pluginGroups>
    <pluginGroup>com.jarastra.remediation</pluginGroup>
  </pluginGroups>
</settings>
```

### Step 2: Run from the Target Project
After local installation, move to the Maven project you want to analyze or remediate:

```bash
cd /path/to/your-maven-project
```

### Step 3: Run the Plugin
JarAstra provides two primary goals:

#### `mvn jarastra:analyze`
-   **When to run**: During regular builds or CI/CD.
-   **Effect**: Scans the project for vulnerabilities and prints a summary. 
-   **Phase**: Automatically bound to the `verify` phase.

#### `mvn jarastra:remediate`
-   **When to run**: When you are ready to apply security patches.
-   **Effect**: Modifies `pom.xml` significantly and performs AST source refactoring.
-   **Simulation**: Use `-Djarastra.dryRun=true` to preview the remediation plan.
-   **Safety**: Automatically backs up files and rolls back if the build fails after remediation.
-   **Phase**: This goal is **not** bound to any phase. It must be called manually.
```bash
# Apply remediation
mvn jarastra:remediate -U

# Simulate remediation (Dry Run)
mvn jarastra:remediate -U -Djarastra.dryRun=true

# Simulate remediation and skip post-upgrade build verification
mvn jarastra:remediate -U -Djarastra.dryRun=true -Djarastra.skipVerification=true
```

### Advanced Remediation Controls
In some cases, you may want to apply structural changes (like Spring Boot 4 migrations) even if they cause temporary build failures. Use these flags for expert control:
- `-Djarastra.skipVerification=true`: Skips the post-upgrade `mvn clean compile` check.
- `-Djarastra.skipRollback=true`: Prevents automatic restoration of backups if the build fails.

---

## 6. First-Run Troubleshooting

### Error: Missing `com.jarastra.remediation.core.*` classes
Example:

- `BuildSystemBridge`
- `ConfigManager`

Cause:

- the installed JarAstra plugin or `core-engine` artifact in `~/.m2` is missing or stale

Fix:

```bash
cd /path/to/JarAstra
mvn clean install
```

Then rerun the command from the target project.

### Error: Ollama model not found
Example:

- `404 - {"error":"model 'llama3' not found"}`
- `404 - {"error":"model 'codellama:7b' not found"}`

Cause:

- `llm.model` points to a model that is not installed locally

Fix:

```bash
ollama list
ollama pull llama3
ollama pull codellama:7b
```

or update `~/.jarastra/jarastra.properties` to a model that already exists.

### Repository metadata warnings / download failures
If Maven logs repeated failures downloading metadata:

- verify your network or proxy configuration
- verify the repositories in your Maven settings are reachable from the current machine
- remember that JarAstra consults remote repository metadata to discover newer versions

### Safe validation command
When validating installation or environment, start with:

```bash
mvn jarastra:remediate -U -Djarastra.dryRun=true -Djarastra.skipVerification=true
```

This confirms:

- the plugin loads
- `core-engine` is present
- configuration is readable
- version discovery starts

without writing real project changes.

---

## 7. Gradle Integration
JarAstra provides professional-grade support for Gradle projects (both Groovy and Kotlin DSL).

### Way A: Using the CLI (Recommended)
The JarAstra CLI automatically detects `build.gradle` or `build.gradle.kts` files and applies the `GradleBuildBridge`.

```bash
# Analyze and Upgrade a Gradle project
java -jar libs/core-engine-1.0.0.jar /path/to/gradle-project --upgrade
```

### Way B: Maven Cross-Build Bridge
A unique feature of JarAstra is the ability to remediate Gradle projects using the Maven Plugin. This is useful in hybrid environments where you want a unified command set.

1.  Navigate to your Gradle project directory.
2.  Run the Maven command (ensure you have the `com.jarastra.remediation` pluginGroup in `settings.xml` as shown in the Maven section):
```bash
mvn jarastra:remediate
```
- **Detection**: The plugin will see the absence of `pom.xml` and the presence of Gradle files, automatically switching to the **Gradle Intelligence Engine**.
- **Transformation**: JarAstra will modify the `dependencies` blocks in your `.gradle` files using regex-based surgical insertion.

---

## 8. Understanding the Remediation Report
After every `--upgrade` or `remediate` run, JarAstra generates `jarastra-remediation-report.md`.

-   **CVE Resolution**: Maps every version change back to the specific security advisory.
-   **AI Reasoning**: Explains *why* a specific version was chosen (e.g., "Maintained framework alignment across group org.springframework").
-   **Breaking Change Advice**: Warns you if an upgrade requires manual code changes (e.g., `javax` package renames).

---

## 9. Enterprise Configuration & Performance

### Machine-Agnostic Setup
JarAstra GA automatically detects your local Maven repository in `~/.m2/repository`. You only need to set `maven.home` in your properties file if using a non-standard path.

### Performance Tuning
For large enterprise reactors, we recommend these settings in `jarastra.properties`:
```properties
# Enable parallel AI reasoning across modules
llm.parallel.enabled=true

# Persistent cache for remote Maven Central lookups
cache.maven.enabled=true

# Custom repository resolution (e.g., Nexus/Artifactory)
maven.repositories=central|https://repo1.maven.org/maven2/,nexus|https://mynexus.local/repo
```

---

**Advanced Tip**: Use the `--offline` flag to skip CVE database updates if you have already synced the local cache.

**Framework Note**: JarAstra's Gradle support is fully compatible with both **Groovy DSL** (`build.gradle`) and **Kotlin DSL** (`build.gradle.kts`).
