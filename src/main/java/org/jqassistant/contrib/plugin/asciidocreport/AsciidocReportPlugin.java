package org.jqassistant.contrib.plugin.asciidocreport;

import static java.util.Collections.singletonList;
import static org.asciidoctor.AttributesBuilder.attributes;
import static org.asciidoctor.OptionsBuilder.options;

import java.io.File;
import java.util.*;

import com.buschmais.jqassistant.core.analysis.api.Result;
import com.buschmais.jqassistant.core.analysis.api.rule.*;
import com.buschmais.jqassistant.core.report.api.ReportContext;
import com.buschmais.jqassistant.core.report.api.ReportException;
import com.buschmais.jqassistant.core.report.api.ReportHelper;
import com.buschmais.jqassistant.core.report.api.ReportPlugin;
import com.buschmais.jqassistant.core.report.api.ReportPlugin.Default;
import com.buschmais.jqassistant.core.rule.api.source.RuleSource;
import com.buschmais.jqassistant.core.shared.asciidoc.AsciidoctorFactory;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.JavaExtensionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Default
public class AsciidocReportPlugin implements ReportPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsciidocReportPlugin.class);

    private static final String PROPERTY_DIRECTORY = "asciidoc.report.directory";
    private static final String PROPERTY_RULE_DIRECTORY = "asciidoc.report.rule.directory";
    private static final String PROPERTY_FILE_INCLUDE = "asciidoc.report.file.include";
    private static final String PROPERTY_FILE_EXCLUDE = "asciidoc.report.file.exclude";

    private static final String DEFAULT_REPORT_DIRECTORY = "asciidoc";
    private static final String DEFAULT_INDEX_FILE = "index.adoc";

    private static final String BACKEND_HTML5 = "html5";
    private static final String CODERAY = "coderay";

    private ReportContext reportContext;

    private File reportDirectory;

    private SourceFileMatcher sourceFileMatcher;

    private Set<RuleSource> ruleSources;

    private Map<String, RuleResult> conceptResults;
    private Map<String, RuleResult> constraintResults;

    @Override
    public void configure(ReportContext reportContext, Map<String, Object> properties) {
        this.reportContext = reportContext;
        File defaultReportDirectory = reportContext.getReportDirectory(DEFAULT_REPORT_DIRECTORY);
        this.reportDirectory = getFile(PROPERTY_DIRECTORY, defaultReportDirectory, properties).getAbsoluteFile();
        if (this.reportDirectory.mkdirs()) {
            LOGGER.info("Created directory '" + this.reportDirectory.getAbsolutePath() + "'.");
        }
        File ruleDirectory = getFile(PROPERTY_RULE_DIRECTORY, null, properties);
        String fileInclude = (String) properties.get(PROPERTY_FILE_INCLUDE);
        String fileExclude = (String) properties.get(PROPERTY_FILE_EXCLUDE);
        this.sourceFileMatcher = new SourceFileMatcher(ruleDirectory, fileInclude, fileExclude);
    }

    private File getFile(String property, File defaultValue, Map<String, Object> properties) {
        String directoryName = (String) properties.get(property);
        return directoryName != null ? new File(directoryName) : defaultValue;
    }

    @Override
    public void begin() {
        ruleSources = new HashSet<>();
        conceptResults = new HashMap<>();
        constraintResults = new HashMap<>();
    }

    @Override
    public void end() throws ReportException {
        Map<File, List<File>> files = sourceFileMatcher.match(ruleSources);
        if (!files.isEmpty()) {
            LOGGER.info("Calling for the Asciidoctor...");
            Asciidoctor asciidoctor = AsciidoctorFactory.getAsciidoctor();
            LOGGER.info("Writing to report directory " + reportDirectory.getAbsolutePath());
            for (Map.Entry<File, List<File>> entry : files.entrySet()) {
                File baseDir = entry.getKey();
                OptionsBuilder optionsBuilder = options().mkDirs(true).baseDir(baseDir).toDir(reportDirectory).backend(BACKEND_HTML5).safe(SafeMode.UNSAFE)
                        .attributes(attributes().experimental(true).sourceHighlighter(CODERAY));
                for (File file : entry.getValue()) {
                    LOGGER.info("-> {}", file.getPath());
                    Document document = asciidoctor.loadFile(file, optionsBuilder.asMap());
                    JavaExtensionRegistry extensionRegistry = asciidoctor.javaExtensionRegistry();
                    IncludeProcessor includeProcessor = new IncludeProcessor(document, conceptResults, constraintResults);
                    extensionRegistry.includeProcessor(includeProcessor);
                    extensionRegistry.inlineMacro(new InlineMacroProcessor());
                    extensionRegistry.treeprocessor(new TreePreprocessor(conceptResults, constraintResults, reportDirectory, reportContext));
                    extensionRegistry.postprocessor(new RuleTogglePostProcessor());
                    asciidoctor.convertFile(file, optionsBuilder);
                    asciidoctor.unregisterAllExtensions();
                }
            }
            LOGGER.info("The Asciidoctor finished his work successfully.");
        }
    }

    @Override
    public void beginGroup(Group group) {
        addRuleSource(group);
    }

    @Override
    public void beginConcept(Concept concept) {
        addRuleSource(concept);
    }

    @Override
    public void beginConstraint(Constraint constraint) {
        addRuleSource(constraint);
    }

    private void addRuleSource(Rule rule) {
        ruleSources.add(rule.getSource());
    }

    @Override
    public void setResult(Result<? extends ExecutableRule> result) {
        // Collect the results for executed concepts and constraints
        ExecutableRule rule = result.getRule();
        if (rule instanceof Concept) {
            this.conceptResults.put(rule.getId(), getRuleResult(result));
        } else if (rule instanceof Constraint) {
            this.constraintResults.put(rule.getId(), getRuleResult(result));
        }
    }

    private RuleResult getRuleResult(Result<? extends ExecutableRule> result) {
        RuleResult.RuleResultBuilder ruleResultBuilder = RuleResult.builder();
        List<String> columnNames = result.getColumnNames();
        ruleResultBuilder.rule(result.getRule()).effectiveSeverity(result.getSeverity()).status(result.getStatus())
                .columnNames(columnNames != null ? columnNames : singletonList("Empty Result"));
        for (Map<String, Object> row : result.getRows()) {
            Map<String, List<String>> resultRow = new LinkedHashMap<>();
            for (Map.Entry<String, Object> rowEntry : row.entrySet()) {
                Object value = rowEntry.getValue();
                List<String> values = new ArrayList<>();
                if (value instanceof Iterable<?>) {
                    for (Object o : ((Iterable) value)) {
                        values.add(ReportHelper.getLabel(o));
                    }
                } else {
                    values.add(ReportHelper.getLabel(value));
                }
                resultRow.put(rowEntry.getKey(), values);
            }
            ruleResultBuilder.row(resultRow);
        }
        return ruleResultBuilder.build();
    }
}
