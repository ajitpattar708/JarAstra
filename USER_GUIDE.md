# JarAstra User Guide: Professional Remediation

Welcome to JarAstra GA. This guide provides step-by-step instructions to configure and run the AI-Native remediation engine.

---

## 1. Local LLM Setup (Recommended)
JarAstra is built for privacy. We recommend using **Ollama** to run reasoning models locally.

1.  **Install Ollama**: Download from [ollama.com](https://ollama.com/).
2.  **Pull the Model**: Run `ollama pull llama3`.
3.  **Configure**: Create `~/.jarastra/jarastra.properties` with:
    ```properties
    llm.provider=ollama
    llm.model=llama3
    llm.base.url=http://localhost:11434
    ```

### Using Cloud Providers (OpenAI)
If you prefer cloud-based analysis, use an OpenAI-compatible configuration:
```properties
llm.provider=openai
llm.openai.model=gpt-4
llm.openai.base.url=https://api.openai.com/v1
llm.api.key=sk-...
```

---

## 2. Using the CLI Tool
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

## 3. Maven Plugin Integration
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

### Step 2: Run the Plugin
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

# Simulate remediation
mvn jarastra:remediate -Djarastra.dryRun=true

### Advanced Remediation Controls
In some cases, you may want to apply structural changes (like Spring Boot 4 migrations) even if they cause temporary build failures. Use these flags for expert control:
- `-Djarastra.skipVerification=true`: Skips the post-upgrade `mvn clean compile` check.
- `-Djarastra.skipRollback=true`: Prevents automatic restoration of backups if the build fails.
```

---

## 4. Understanding the Remediation Report
After every `--upgrade` or `remediate` run, JarAstra generates `jarastra-remediation-report.md`.

-   **CVE Resolution**: Maps every version change back to the specific security advisory.
-   **AI Reasoning**: Explains *why* a specific version was chosen (e.g., "Maintained framework alignment across group org.springframework").
-   **Breaking Change Advice**: Warns you if an upgrade requires manual code changes (e.g., `javax` package renames).

---

## 5. Enterprise Configuration & Performance

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
