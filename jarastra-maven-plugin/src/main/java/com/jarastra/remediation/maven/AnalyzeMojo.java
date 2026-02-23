package com.jarastra.remediation.maven;

import com.jarastra.remediation.core.RemediationApplication;
import com.jarastra.remediation.core.AnalysisResult;
import com.jarastra.remediation.core.analysis.MetadataExtractor;
import com.jarastra.remediation.core.analysis.UsageIndexer;
import com.jarastra.remediation.core.bridge.maven.MavenBuildBridge;
import com.jarastra.remediation.core.config.ConfigManager;
import com.jarastra.remediation.core.model.DependencyNode;
import com.jarastra.remediation.core.security.SecurityService;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "analyze", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE)
public class AnalyzeMojo extends AbstractJarAstraMojo {

    @Override
    public void execute() throws MojoExecutionException {
        try {
            getLog().info("JarAstra - Running Analysis...");
            ConfigManager config = createConfig();
            
            RemediationApplication engine = RemediationApplication.builder()
                    .bridge(new MavenBuildBridge(config))
                    .usageIndexer(new UsageIndexer())
                    .securityService(new SecurityService(config))
                    .metadataExtractor(new MetadataExtractor())
                    .build();

            AnalysisResult result = engine.analyzeProject(projectBaseDir.toPath(), offline, true);
            DependencyNode root = result.getRoot();
            
            getLog().info("Analysis completed for: " + root.getGAV());
            if (root.getVulnerabilities() != null && !root.getVulnerabilities().isEmpty()) {
                getLog().warn("Vulnerabilities found: " + root.getVulnerabilities().size());
            } else {
                getLog().info("No critical vulnerabilities detected.");
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error during JarAstra analysis", e);
        }
    }
}
