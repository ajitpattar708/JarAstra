# JarAstra Architecture Design

JarAstra is an AI-Native Dependency Management and Security Upgrade tool. It leverages LLMs to reason about complex dependency graphs and perform safe, verified upgrades.

## Core Components

### 1. Intelligence Engine
The core analysis component of JarAstra, optimized for **Local LLMs** (e.g., Ollama).
- **Provider Layer**: Primarily supports **Ollama** for local-first, secure reasoning. Supports Generic API for flexibility.
- **Dynamic Reasoning (Prompt-driven Logic)**: In an AI-Native tool, "Prompts" are the semantic instructions that guide the LLM in resolving dependency complexity. This replaces thousands of lines of hardcoded edge-case logic with flexible reasoning.
- **Experience Store**: A specialized persistent storage that records outcomes to reinforce future decisions.

### Advanced Analysis & Security
- **OSV Intelligence**: Correlates vulnerability data with project usage for context-aware scoring.
- **Reachability Analysis**: Uses ASM to build a project-wide call graph. It verifies if vulnerable methods (extracted from OSV) are actually reachable via project code.
- **ASM-Powered Usage Indexing**: Records class and **method-level** usages to predict braking changes with high precision.
- **Deep Metadata Extraction**: Detects **JPMS (Java Modules)**, **Multi-Release** manifest flags, and reflective access fragility.

### Intelligent Remediation (The Expert Remediation Layer)
- **Deep Transitive Resolution**: Identifies vulnerabilities buried at level 4, 5, or 6+.
- **Automatic Force-Overrides**: Automatically injects `<dependencyManagement>` (Maven) or `constraints` (Gradle) at the root level to remediate deep vulnerabilities without manual intervention.
- **Self-Healing LLM Loop**: Analyzes build failure logs using local LLMs (Llama 3) to suggest specific recovery actions (rollback or exclusion).
- **Safe Migration**: AI-driven refactoring of source code for package name changes (e.g., `javax.*` to `jakarta.*`).
    - **Fragility Detection**: Detects reflective calls that might break during semantic package migrations.

### 3. Build Bridges & Remediation
Supports both **Maven** and **Gradle** systems for full tree traversal.
- **Deep Graph Traversal**: Automatically resolves and scans dependencies up to any depth (4th, 5th, 6th+ levels).
- **Gradle Tooling Bridge**: Integrated `GradleBuildBridge` using the Tooling API for native dependency graph resolution.

### 4. Refactoring Agent
The execution module of JarAstra.
- **Build File Modification**: Regex-based precision edits for `pom.xml` and `build.gradle`.
- **Source Code Migration**: AI-driven package name mapping and source-level refactoring.

### 5. Reporting & Observability (GA Only)
- **Markdown Remediation Report**: Generates a human-readable summary of all changes, AI advice, and CVE resolutions.
- **JSON Project State**: Exports the full dependency tree and security metadata to JSON for downstream consumption.

### 6. Performance & Scale (The GA Optimization)
- **Parallel AI Researcher**: Uses `parallelStream` and `CompletableFuture` to research multiple project-wide dependency upgrades concurrently.
- **Version Persistence Layer**: Implements a local JSON-based cache for Maven Central version streams to reduce network latency and enable cross-module reuse.

### 7. Self-Healing Loop (Build Verifier)
- Executes project builds post-upgrade.
- Captures error logs and feeds them back to the Intelligence Engine for "Reflection" and "Recovery advice".

## Build System Matrix
| Feature | Maven | Gradle |
| :--- | :--- | :--- |
| Dependency Resolution | Native Aether/Resolver | Gradle Tooling API |
| Remediation Injection | <dependencyManagement> | constraints { ... } |
| Multi-module Support | Recursive POM Scanning | Tooling API Module Discovery |
| Build Verification | mvn compile | gradle classes |

---
*JarAstra: AI-Native Security at Enterprise Scale.*
