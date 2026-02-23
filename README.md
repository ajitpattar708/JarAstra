# JarAstra GA
### AI-Native Dependency Intelligence & Professional Remediation

JarAstra is an enterprise-grade, AI-powered tool designed to master the complexity of modern Java dependency trees. Unlike traditional scanners that only report issues, JarAstra uses **Local LLMs (Ollama)** to reason about breaking changes and **Automated Remediation** to surgically patch vulnerabilities—even those buried 6 levels deep in your transitive graph.

---

## 🚀 Key Features

### 🧠 Expert Reasoning Layer
- **Parallel AI Researching**: Concurrently resolves safe versions for complex dependency families (Spring, Helidon, Jackson) using `CompletableFuture`.
- **Local-First AI**: Optimized for Llama 3/CodeLlama via Ollama. No source code or proprietary data ever leaves your machine.
- **Reachability Analysis**: Correlates OSV vulnerability data with an **ASM-powered Call Graph** to verify if a CVE is actually exploitable in your code.

### 🛡️ Smart Remediation
- **AST-Based Source Refactoring (New)**: Uses JavaParser for deep, structurally-aware migrations (e.g., `javax.*` to `jakarta.*`).
- **Pass-Based Atomic Rollbacks**: Automatically reverts changes to the last known good state if a remediation pass breaks your build.
- **Dry-Run Simulation**: Test high-impact remediations in simulation mode to verify AI strategy before applying any changes.
- **Holistic Reports**: Generates professional Markdown logs (`jarastra-remediation-report.md`) detailing AI advice and CVE resolution mapping.
- **Self-Healing Loop**: Automatically analyzes build logs after an upgrade to suggest surgical fixes for classpath conflicts or compilation errors.

---

## 🛠️ Quick Start

### 1. Prerequisites
- **Java**: 17 or higher
- **Local LLM**: [Ollama](https://ollama.com/) installed and running (`ollama run llama3`)

### 2. Global Configuration
Create a configuration file at `~/.jarastra/jarastra.properties` (or `%USERPROFILE%\.jarastra\jarastra.properties` on Windows):
```properties
llm.provider=ollama
llm.model=llama3
```

### 3. Usage Modes

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
Add the plugin to your project for continuous protection:
```bash
# Run remediation with auto-healing and rollback safety
mvn jarastra:remediate -U

# Simulate remediation (Dry Run)
mvn jarastra:remediate -Djarastra.dryRun=true
```
*(See the [User Guide](USER_GUIDE.md) for `settings.xml` setup)*

---

## 📖 Deep Dives
- **[User Guide](USER_GUIDE.md)**: Detailed configuration, Maven Plugin setup, and Troubleshooting.
- **[Architecture](ARCHITECTURE.md)**: Insights into the AI-Native design and ASM-powered analysis.

---

## ⚖️ License
Licensed under the Apache License, Version 2.0.
