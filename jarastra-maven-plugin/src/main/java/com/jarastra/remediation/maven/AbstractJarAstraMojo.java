package com.jarastra.remediation.maven;

import com.jarastra.remediation.core.config.ConfigManager;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import java.io.File;

public abstract class AbstractJarAstraMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected File projectBaseDir;

    @Parameter(property = "jarastra.offline", defaultValue = "false")
    protected boolean offline;

    @Parameter(property = "jarastra.llm.provider")
    protected String llmProvider;

    @Parameter(property = "jarastra.llm.model")
    protected String llmModel;

    @Parameter(property = "jarastra.llm.endpoint")
    protected String llmEndpoint;

    protected ConfigManager createConfig() {
        if (llmProvider != null) System.setProperty("llm.provider", llmProvider);
        if (llmModel != null) System.setProperty("llm.model", llmModel);
        if (llmEndpoint != null) System.setProperty("llm.endpoint", llmEndpoint);
        return new ConfigManager();
    }
}
