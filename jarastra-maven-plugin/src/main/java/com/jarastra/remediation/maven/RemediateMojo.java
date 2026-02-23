package com.jarastra.remediation.maven;

import com.jarastra.remediation.core.RemediationApplication;
import com.jarastra.remediation.core.AnalysisResult;
import com.jarastra.remediation.core.analysis.MetadataExtractor;
import com.jarastra.remediation.core.analysis.UsageIndexer;
import com.jarastra.remediation.core.ai.RemediationIntelligence;
import com.jarastra.remediation.core.bridge.BuildSystemBridge;
import com.jarastra.remediation.core.bridge.gradle.GradleBuildBridge;
import com.jarastra.remediation.core.bridge.maven.MavenBuildBridge;
import com.jarastra.remediation.core.config.ConfigManager;
import com.jarastra.remediation.core.model.DependencyNode;
import com.jarastra.remediation.core.refactor.RemediationStrategist;
import com.jarastra.remediation.core.refactor.CodeRefactorer;
import com.jarastra.remediation.core.refactor.VersionResolver;
import com.jarastra.remediation.core.refactor.VersionResolver.ResolutionResult;
import com.jarastra.remediation.core.report.RemediationReport;
import com.jarastra.remediation.core.report.RemediationReport.UpgradeEntry;
import com.jarastra.remediation.core.security.SecurityService;
import com.jarastra.remediation.core.util.BackupService;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mojo(name = "remediate", requiresDependencyResolution = ResolutionScope.COMPILE)
public class RemediateMojo extends AbstractJarAstraMojo {

    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_RESET = "\u001B[0m";

    @Parameter(property = "jarastra.output", defaultValue = "${project.basedir}/upgraded")
    private File outputDirectory;

    @Parameter(property = "jarastra.dryRun", defaultValue = "false")
    private boolean dryRun;

    @Parameter(property = "jarastra.skipVerification", defaultValue = "false")
    private boolean skipVerification;

    @Parameter(property = "jarastra.skipRollback", defaultValue = "false")
    private boolean skipRollback;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> remoteRepos;

    @Component
    private RepositorySystem repoSystem;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            getLog().info("JarAstra - Initializing upgrades... [Build: 1.0.0-GA]");
            ConfigManager config = createConfig();
            
            Path currentProjectPath = projectBaseDir.toPath();
            
            if (outputDirectory != null) {
                getLog().info("Safe Upgrade Mode: Cloning project to " + outputDirectory.getAbsolutePath());
                try {
                    copyDirectory(currentProjectPath, outputDirectory.toPath());
                    getLog().info("Project successfully cloned to: " + outputDirectory.getAbsolutePath());
                    currentProjectPath = outputDirectory.toPath();
                } catch (Exception e) {
                    getLog().error("Failed to clone project for safe remediation. Proceeding with in-place modification.", e);
                }
            } else {
                getLog().warn("Safe Upgrade Mode DISABLED: 'jarastra.output' is null. Direct modification active.");
            }

            // ---- Determine active bridge ----
            // The Maven plugin can remediate Gradle projects too if invoked from the containing dir.
            BuildSystemBridge activeBridge;
            Path buildFilePath;
            if (java.nio.file.Files.exists(currentProjectPath.resolve("pom.xml"))) {
                MavenBuildBridge mavenBridge = new MavenBuildBridge(config);
                mavenBridge.setMavenContext(repoSystem, repoSession, remoteRepos);
                activeBridge = mavenBridge;
                buildFilePath = currentProjectPath.resolve("pom.xml");
                getLog().info("Maven Integration: Utilizing plugin context for repository resolution");
            } else if (java.nio.file.Files.exists(currentProjectPath.resolve("build.gradle"))) {
                activeBridge = new GradleBuildBridge(config);
                buildFilePath = currentProjectPath.resolve("build.gradle");
                getLog().info("Gradle Integration: Using build.gradle for dependency resolution");
            } else if (java.nio.file.Files.exists(currentProjectPath.resolve("build.gradle.kts"))) {
                activeBridge = new GradleBuildBridge(config);
                buildFilePath = currentProjectPath.resolve("build.gradle.kts");
                getLog().info("Gradle Integration (KTS): Using build.gradle.kts for dependency resolution");
            } else {
                // Fallback: Maven bridge using plugin context (multi-module root scenario)
                MavenBuildBridge mavenBridge = new MavenBuildBridge(config);
                mavenBridge.setMavenContext(repoSystem, repoSession, remoteRepos);
                activeBridge = mavenBridge;
                buildFilePath = currentProjectPath.resolve("pom.xml");
                getLog().warn("No recognized build file found. Defaulting to Maven/pom.xml");
            }

            RemediationIntelligence intelligence = new RemediationIntelligence(config);
            BackupService backupService = new BackupService(currentProjectPath);
            CodeRefactorer agent = new CodeRefactorer(intelligence);
            agent.setDryRun(dryRun);
            agent.setBackupService(backupService);

            VersionResolver discoveryAgent = new VersionResolver(intelligence, config, activeBridge);
            RemediationStrategist strategyAgent = new RemediationStrategist(intelligence, config);

            // ... (report builder remains same) ...
            RemediationReport report = RemediationReport.builder()
                    .projectName(project.getName() != null && !project.getName().isEmpty() ? project.getName() : project.getArtifactId())
                    .projectVersion(project.getVersion())
                    .startTime(System.currentTimeMillis())
                    .build();

            int maxPasses = 3;
            int currentPass = 1;
            Map<Path, Path> currentPassBackups = new java.util.HashMap<>();

            while (currentPass <= maxPasses) {
                getLog().info("\n--- [REMEDIATION PASS " + currentPass + "] ---");
                if (dryRun) getLog().warn("[Dry Run] No files will be modified in this pass.");
                
                AnalysisResult result = RemediationApplication.builder()
                        .bridge(activeBridge)
                        .usageIndexer(new UsageIndexer())
                        .securityService(new SecurityService(config))
                        .metadataExtractor(new MetadataExtractor())
                        .build()
                        .analyzeProject(currentProjectPath, offline, true);

                DependencyNode root = result.getRoot();
                report.setJavaVersion(root.getProjectJavaVersion());
                report.setFramework(root.getFramework());
                List<RemediationStrategist.UpgradePlan> strategy = strategyAgent.deviseStrategy(root, root.getProjectJavaVersion());
                
                if (strategy.isEmpty()) {
                    getLog().info("No vulnerabilities identified in Pass " + currentPass + ".");
                    break;
                }

                getLog().info("AI Strategy suggests " + strategy.size() + " upgrade actions.");
                boolean upgradesAppliedInPass = false;

                // (Resolution logic remains same, but we update the apply logic)
                // ... [Keeping resolution logic as is for now to avoid massive diffs] ...
                // Parallel Version Resolution
                Map<RemediationStrategist.UpgradePlan, VersionResolver.ResolutionResult> resolvedMappings = new java.util.concurrent.ConcurrentHashMap<>();
                java.util.concurrent.ExecutorService analysisExecutor = java.util.concurrent.Executors.newFixedThreadPool(10);
                List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

                for (RemediationStrategist.UpgradePlan plan : strategy) {
                    if (discoveryAgent.isLikelyFramework(plan.getNode()) || plan.getType() == RemediationStrategist.UpgradeType.RELOCATE) {
                        ResolutionResult resolution = discoveryAgent.findSafeUpgrade(plan.getNode(), root.getProjectJavaVersion(), plan.getTargetVersion());
                        if (resolution != null && resolution.getVersion() != null) {
                            // Sync relocation if resolver discovered a canonical move
                            if (resolution.getNewGroupId() != null) {
                                plan.setType(RemediationStrategist.UpgradeType.RELOCATE);
                                plan.setNewGroupId(resolution.getNewGroupId());
                            }
                            if (resolution.getNewArtifactId() != null) {
                                plan.setNewArtifactId(resolution.getNewArtifactId());
                            }
                            resolvedMappings.put(plan, resolution);
                        }
                    } else if (plan.getType() == RemediationStrategist.UpgradeType.REMOVE_VERSION) {
                         VersionResolver.ResolutionResult fake = new VersionResolver.ResolutionResult();
                         fake.setVersion("MANAGED");
                         fake.setAdvice("Managed Dependency");
                         resolvedMappings.put(plan, fake);
                    } else {
                        futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                            VersionResolver.ResolutionResult resolution = discoveryAgent.findSafeUpgrade(plan.getNode(), root.getProjectJavaVersion(), plan.getTargetVersion());
                            if (resolution != null && resolution.getVersion() != null) {
                                resolvedMappings.put(plan, resolution);
                            }
                        }, analysisExecutor));
                    }
                }

                if (!futures.isEmpty()) {
                    java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
                }
                analysisExecutor.shutdown();

                // Sequential Upgrade Application
                for (RemediationStrategist.UpgradePlan plan : strategy) {
                    VersionResolver.ResolutionResult resolution = resolvedMappings.get(plan);
                    if (resolution == null || resolution.getVersion() == null) continue;

                    String safeVersion = resolution.getVersion();
                    String advice = resolution.getAdvice();
                    String minJdk = resolution.getMinJdk();
                    boolean breaking = resolution.isBreaking();
                    
                    // Sync relocation metadata if discovered during resolution
                    if (resolution.getNewGroupId() != null) {
                        plan.setNewGroupId(resolution.getNewGroupId());
                        String aid = plan.getNode().getArtifactId();
                        boolean isParent = plan.getType() == RemediationStrategist.UpgradeType.PARENT || 
                                           aid.contains("-parent") || aid.equals("helidon-main");

                        if (!isParent) {
                            plan.setType(RemediationStrategist.UpgradeType.RELOCATE);
                            getLog().warn(ANSI_RED + ANSI_BOLD + "[RELOCATION] Promoted plan to RELOCATE for " + aid + ": " + plan.getNode().getGroupId() + " -> " + resolution.getNewGroupId() + ANSI_RESET);
                        } else {
                            getLog().warn(ANSI_RED + ANSI_BOLD + "[RELOCATION] Redirecting Parent " + aid + " to " + resolution.getNewGroupId() + " (Ensuring PARENT type)" + ANSI_RESET);
                            plan.setType(RemediationStrategist.UpgradeType.PARENT);
                        }
                    }
                    if (resolution.getNewArtifactId() != null) {
                        plan.setNewArtifactId(resolution.getNewArtifactId());
                        String aid = plan.getNode().getArtifactId();
                        boolean isParent = plan.getType() == RemediationStrategist.UpgradeType.PARENT || 
                                           aid.contains("-parent") || aid.equals("helidon-main");
                        if (!isParent) {
                            plan.setType(RemediationStrategist.UpgradeType.RELOCATE);
                        }
                    }
                    
                    if (advice != null && (advice.contains("WARNING") || advice.contains("BREAKING") || breaking)) {
                        getLog().warn(ANSI_RED + ANSI_BOLD + "[CRITICAL ADVISORY] " + advice + (breaking ? " (IDENTIFIED BREAKING CHANGE)" : "") + ANSI_RESET);
                        report.addWarning(plan.getNode().getArtifactId() + ": " + advice);
                    }

                    // Detect Major Version Hop
                    String currentVer = plan.getNode().getVersion();
                    if (currentVer != null && safeVersion != null && !"MANAGED".equals(safeVersion)) {
                        try {
                            String currentMajor = currentVer.split("[.\\-v]")[0].replaceAll("[^0-9]", "0");
                            String targetMajor = safeVersion.split("[.\\-v]")[0].replaceAll("[^0-9]", "0");
                            if (!currentMajor.equals(targetMajor)) {
                                getLog().warn(ANSI_RED + ANSI_BOLD + "[HIGH RISK] Major Version Jump: " + currentVer + " -> " + safeVersion + " (Manual code changes likely required)" + ANSI_RESET);
                                report.addWarning(plan.getNode().getArtifactId() + ": Major version jump from " + currentVer + " to " + safeVersion);
                            }
                        } catch (Exception e) {}
                    }

                    getLog().info("Applying upgrade: " + plan.getNode().getGAV() + " -> " + safeVersion);
                    Path buildFile = buildFilePath;
                    
                    // Track backup for pass rollback
                    String uniqueName = buildFile.toAbsolutePath().toString().replaceAll("[^a-zA-Z0-9]", "_") + ".bak";
                    currentPassBackups.put(buildFile, backupService.getBackupDir().resolve(uniqueName));

                    if (minJdk != null) {
                        try {
                            int reqJdk = Integer.parseInt(minJdk.replace("1.", "").trim());
                            String currentJdkStr = root.getProjectJavaVersion();
                            int currentJdk = 0;
                            if (currentJdkStr != null) {
                                currentJdk = Integer.parseInt(currentJdkStr.replace("1.", "").trim());
                            }

                            // Host JDK Awareness
                            String runtimeJdkStr = System.getProperty("java.version").replace("1.", "").split("\\.")[0];
                            int runtimeJdk = Integer.parseInt(runtimeJdkStr);
                            
                            if (reqJdk > currentJdk) {
                                if (reqJdk > runtimeJdk) {
                                    getLog().warn("[System Constraint] Required JDK (" + reqJdk + ") > Host JDK (" + runtimeJdk + "). Restricting upgrade to " + runtimeJdk + " to preserve build integrity.");
                                    reqJdk = runtimeJdk; // Cap to runtime version
                                }

                                if (reqJdk > currentJdk) {
                                    getLog().warn(ANSI_RED + ANSI_BOLD + "[SYSTEM ALIGNMENT] Current JDK (" + (currentJdk == 0 ? "unknown" : currentJdk) + ") < Required JDK " + reqJdk + ". Force-upgrading project JDK in build file..." + ANSI_RESET);
                                    agent.upgradeProjectJavaVersion(buildFile, String.valueOf(reqJdk));
                                    root.setProjectJavaVersion(String.valueOf(reqJdk));
                                }
                            }
                        } catch (Exception e) {
                            getLog().debug("Could not parse JDK version for auto-upgrade: " + e.getMessage());
                        }
                    }

                    if (agent.performUpgrade(buildFile, plan, safeVersion)) {
                        upgradesAppliedInPass = true;
                        report.addUpgrade(UpgradeEntry.builder()
                                .gav(plan.getNode().getGAV())
                                .fromVersion(plan.getNode().getVersion())
                                .toVersion(safeVersion)
                                .type(plan.getType().name())
                                .aiAdvice(advice)
                                .minJdk(minJdk)
                                .breaking(breaking)
                                .skippedMajorVersion(resolution.getSkippedMajorVersion())
                                .skipReason(resolution.getSkipReason())
                                .success(true)
                                .build());
                        
                        if (resolution.getSkippedMajorVersion() != null) {
                            getLog().warn("[ADVISORY] Higher version available but skipped: " + resolution.getSkippedMajorVersion() + " - Reason: " + resolution.getSkipReason());
                        }

                        if (advice != null && advice.contains("javax") && advice.contains("jakarta")) {
                            getLog().info("Breaking change detected. Starting source code migration (javax -> jakarta)...");
                            try {
                                Path srcDir = currentProjectPath.resolve("src/main/java");
                                agent.migrateSourcePackages(srcDir, Map.of("javax.", "jakarta."));
                            } catch (Exception e) {
                                getLog().error("Source migration failed", e);
                            }
                        }
                    }
                }

                if (!upgradesAppliedInPass) {
                    getLog().info("No changes could be applied in this pass. Stopping.");
                    break;
                }

                if (dryRun) {
                    getLog().info("[Dry Run] Pass " + currentPass + " complete. Skipping verification.");
                    break;
                }
                
                if (skipVerification) {
                    getLog().info("Build verification SKIPPED via configuration.");
                    currentPassBackups.clear(); // Commit automatically if skipping 
                } else {
                    // Self-Healing: Build verification
                    getLog().info("Verifying build integrity...");
                    com.jarastra.remediation.core.build.BuildVerifier verifier = new com.jarastra.remediation.core.build.BuildVerifier(config);
                    com.jarastra.remediation.core.build.VerificationResult vResult = verifier.verifyBuild(currentProjectPath);
                    
                    if (!vResult.isSuccessful()) {
                        getLog().error("Build FAILED post-upgrade. See details above:");
                        getLog().error("---------------------------------------------------------");
                        getLog().error(vResult.getLog());
                        getLog().error("---------------------------------------------------------");
                        
                        if (skipRollback) {
                            getLog().warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                            getLog().warn("!! CRITICAL: Build FAILED but Rollback is DISABLED via configuration. !!");
                            getLog().warn("!! Your 'upgraded' project is now in a BROKEN state. Check logs!       !!");
                            getLog().warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                            currentPassBackups.clear();
                        } else {
                            getLog().error("#########################################################");
                            getLog().error("## ERROR: Build FAILED post-upgrade. Rolling back...   ##");
                            getLog().error("## To force changes despite errors, use:               ##");
                            getLog().error("## -Djarastra.skipRollback=true                        ##");
                            getLog().error("#########################################################");
                            backupService.performRollback(currentPassBackups);
                            
                            String healing = strategyAgent.getHealingAdvice(vResult.getLog(), strategy);
                            if (healing != null) {
                                getLog().warn("[AI REFLECTION] " + healing);
                                report.addWarning("Build failure recovery: " + healing);
                            }
                            break; // Stop on failure if rolling back
                        }
                    } else {
                        getLog().info("Build SUCCESSFUL. Project integrity verified.");
                        currentPassBackups.clear(); // Pass committed
                    }
                }


                if (currentPass < maxPasses) {
                    getLog().info("Pass " + currentPass + " complete. Re-analyzing dependencies...");
                }
                currentPass++;
            }
            report.setEndTime(System.currentTimeMillis());
            getLog().info("\nRemediation process finished.");

            // Write Report
            try {
                Path reportPath = currentProjectPath.resolve("jarastra-remediation-report.md");
                Files.writeString(reportPath, report.generateMarkdown());
                getLog().info("Remediation report generated at: " + reportPath.toAbsolutePath());
            } catch (Exception e) {
                getLog().error("Failed to generate remediation report", e);
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error during JarAstra remediation", e);
        }
    }

    private boolean isIgnored(Path path) {
        for (Path component : path) {
            String name = component.toString().toLowerCase();
            if (name.equals("upgraded") || name.equals("target") || name.equals("build") || name.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private void copyDirectory(Path source, Path target) throws Exception {
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
        
        final Path absoluteSource = source.toAbsolutePath();
        final Path absoluteTarget = target.toAbsolutePath();

        Files.walk(source)
                .filter(path -> {
                    Path absPath = path.toAbsolutePath();
                    // Prevent infinite recursion if target is inside source
                    return !absPath.startsWith(absoluteTarget);
                })
                .filter(path -> !path.equals(source))     // Skip root
                .filter(path -> !isIgnored(path))        // Use helper for robust ignoring
                .forEach(path -> {
                    try {
                        Path dest = target.resolve(source.relativize(path));
                        if (Files.isDirectory(path)) {
                            if (!Files.exists(dest)) Files.createDirectories(dest);
                        } else {
                            Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to copy file: " + path, e);
                    }
                });
    }
}
