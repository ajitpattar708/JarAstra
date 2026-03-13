# JarAstra GA
### AI-Native & Framework-Agnostic Dependency Intelligence

JarAstra is an enterprise-grade, AI-powered tool designed to master the complexity of modern Java dependency trees. **Completely Framework-Agnostic**, it provides seamless remediation across Spring Boot, Micronaut, Helidon, Quarkus, and standard Jakarta EE projects. Unlike traditional scanners, JarAstra uses **Local LLMs (Ollama)** to reason about breaking changes and **Automated Remediation** to surgically patch vulnerabilities—even those buried 6 levels deep in your transitive graph.

---

## 🌐 Framework-Agnostic Intelligence
Unlike tools tied to specific ecosystems, JarAstra provides deep, context-aware remediation tailored for the entire Java backend landscape:
- **Spring Boot**: Automated v2.x to v3.x/v4.x migrations, starter renames, and property mapping.
- **Micronaut**: Full v3 to v4 AST-level support, including thread-model updates and annotation migrations.
- **Helidon**: Platform-wide version alignment and Nima/Virtual Thread compatibility verification.
- **Quarkus & Jakarta EE**: Unified security patching and `javax` to `jakarta` source refactoring.

## 🚀 Key Features

### 🛠️ Universal Build Bridge
- **Multi-Build Support**: Unified analysis and patching for both **Maven** and **Gradle** projects.
- **Ecosystem Intelligence**: AI models are trained to recognize and respect the specific upgrade rules of different dependency families (e.g., Jackson relocation patterns, Hibernate migrations).

### 🧠 Expert Reasoning Layer
- **Parallel AI Researching**: Concurrently resolves safe versions for complex dependency families using `CompletableFuture`.
- **Local-First AI**: Optimized for Llama 3/CodeLlama via Ollama. No source code or proprietary data ever leaves your machine.
- **Reachability Analysis**: Correlates OSV vulnerability data with an **ASM-powered Call Graph** to verify if a CVE is actually exploitable in your bytecode.

### 🛡️ Smart Remediation
- **AST-Based Source Refactoring (New)**: Uses JavaParser for deep, structurally-aware migrations (e.g., `javax.*` to `jakarta.*`).
- **Pass-Based Atomic Rollbacks**: Automatically reverts changes to the last known good state if a remediation pass breaks your build.
- **Self-Healing Loop**: Automatically analyzes build logs after an upgrade to suggest surgical fixes for classpath conflicts or compilation errors.

---

## 🛠️ Quick Start

### 1. Prerequisites
- **Java**: 17 or higher
- **Maven**: 3.8.5 or higher
- **Local LLM**: [Ollama](https://ollama.com/) installed and running

Supported local model examples:

- `llama3`
- `codellama:7b`

### 2. Build And Install JarAstra Locally
Before you can use `jarastra:...` goals from another Maven project, install JarAstra into your local Maven repository:

```bash
cd /path/to/JarAstra
mvn clean install
```

Notes:

- This step is required because the Maven plugin depends on the local `core-engine` artifact.
- In this repository, the checked-in `core-engine` source is not present; the build populates the module from `libs/core-engine-1.0.0.jar` during install.

### 3. Global Configuration
Create a configuration file at `~/.jarastra/jarastra.properties` (or `%USERPROFILE%\.jarastra\jarastra.properties` on Windows):
```properties
llm.provider=ollama
llm.model=llama3
llm.base.url=http://localhost:11434
```

If you already have Code Llama locally, this also works:

```properties
llm.provider=ollama
llm.model=codellama:7b
llm.base.url=http://localhost:11434
```

### 4. Verify Ollama Model Availability
JarAstra will fail if the configured Ollama model does not exist locally.

```bash
ollama list
```

If your configured model is not listed, either pull it:

```bash
ollama pull llama3
```

or, if you want to use Code Llama:

```bash
ollama pull codellama:7b
```

or update `~/.jarastra/jarastra.properties` to a model that already exists on your machine.

### 5. Usage Modes

#### Mode A: CLI (Binary Core)
Use the protected engine jar directly from the `libs` folder:
```bash
# Analyze Only
java -jar libs/core-engine-1.0.0.jar /path/to/project

# Dry Run (Preview AI strategy & AST changes without writing)
java -jar libs/core-engine-1.0.0.jar /path/to/project --dry-run

# AI-Powered Upgrade (With Auto-Rollback safety)
java -jar libs/core-engine-1.0.0.jar /path/to/project --upgrade
```

#### Mode B: Maven Integration (Recommended)
From the target Maven project:
```bash
# Optional: add the plugin group to ~/.m2/settings.xml so `jarastra:` shorthand works
# Then run from your target project directory

# Run remediation with auto-healing and rollback safety
mvn jarastra:remediate -U

# Simulate remediation (Dry Run)
mvn jarastra:remediate -U -Djarastra.dryRun=true

# Simulate without post-upgrade verification
mvn jarastra:remediate -U -Djarastra.dryRun=true -Djarastra.skipVerification=true
```
*(See the [User Guide](USER_GUIDE.md) for `settings.xml` setup)*

### 6. Common First-Run Issues

If `mvn jarastra:remediate -U` fails, check these first:

- Run `mvn clean install` in the JarAstra repository again to refresh the plugin and `core-engine` artifacts in `~/.m2`.
- Confirm `ollama list` contains the model configured in `~/.jarastra/jarastra.properties`.
- If you see `model 'llama3' not found` or a similar Ollama model error, either pull that model or switch to an installed one such as `codellama:7b`.
- If you see repository metadata download warnings, your Maven environment may not be able to reach the configured repositories or proxy.

---

## 📖 Deep Dives
- **[User Guide](USER_GUIDE.md)**: Detailed configuration, Maven Plugin setup, and Troubleshooting.
- **[Architecture](ARCHITECTURE.md)**: Insights into the AI-Native design and ASM-powered analysis.

---

## ⚖️ License
Licensed under the Apache License, Version 2.0.
