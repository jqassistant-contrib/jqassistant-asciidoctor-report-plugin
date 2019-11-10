package org.jqassistant.contrib.plugin.asciidocreport;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.*;

import com.buschmais.jqassistant.core.analysis.api.Result;
import com.buschmais.jqassistant.core.analysis.api.Result.Status;
import com.buschmais.jqassistant.core.analysis.api.rule.Concept;
import com.buschmais.jqassistant.core.analysis.api.rule.Constraint;
import com.buschmais.jqassistant.core.analysis.api.rule.RuleException;
import com.buschmais.jqassistant.core.analysis.api.rule.RuleSet;
import com.buschmais.jqassistant.core.report.api.ReportContext;
import com.buschmais.jqassistant.core.report.api.ReportException;
import com.buschmais.jqassistant.core.report.api.ReportPlugin;
import com.buschmais.jqassistant.core.report.impl.ReportContextImpl;
import com.buschmais.jqassistant.core.rule.api.reader.RuleConfiguration;
import com.buschmais.jqassistant.core.rule.api.reader.RuleParserPlugin;
import com.buschmais.jqassistant.core.rule.api.source.FileRuleSource;
import com.buschmais.jqassistant.core.rule.api.source.RuleSource;
import com.buschmais.jqassistant.core.rule.impl.reader.AsciidocRuleParserPlugin;
import com.buschmais.jqassistant.core.rule.impl.reader.RuleParser;
import com.buschmais.jqassistant.core.shared.io.ClasspathResource;

import org.jqassistant.contrib.plugin.asciidocreport.plantuml.component.ComponentDiagramReportPlugin;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;

public class AbstractAsciidocReportPluginTest {

    protected Map<String, ReportPlugin> reportPlugins;

    protected File ruleDirectory;

    protected File outputDirectory = new File("target/");

    protected RuleSet ruleSet;

    @BeforeEach
    public final void setUp() throws RuleException {
        File classesDirectory = ClasspathResource.getFile(AbstractAsciidocReportPluginTest.class, "/");
        ruleDirectory = new File(classesDirectory, "jqassistant");
        ruleSet = getRuleSet(ruleDirectory, "index.adoc", "additional-rules/importedRules.adoc", "additional-rules/includedRules.adoc");
        reportPlugins = new HashMap<>();
        reportPlugins.put("asciidoc", new AsciidocReportPlugin());
        reportPlugins.put("plantuml-component-diagram", new ComponentDiagramReportPlugin());
        for (ReportPlugin reportPlugin : reportPlugins.values()) {
            reportPlugin.initialize();
        }
    }

    protected final ReportContext configureReportContext(Map<String, Object> properties) throws ReportException {
        ReportContext reportContext = new ReportContextImpl(outputDirectory);
        for (ReportPlugin reportPlugin : reportPlugins.values()) {
            reportPlugin.configure(reportContext, properties);
        }
        return reportContext;
    }

    protected final void verifyRule(Document document, String id, String expectedDescription, Status expectedStatus, String expectedSeverity) {
        Element rule = document.getElementById(id);
        Element title = rule.getElementsByClass("title").first();
        assertThat(title).isNotNull();
        assertThat(title.text()).isEqualTo(expectedDescription);
        Element status = title.getElementsByTag("span").first();
        assertThat(status).isNotNull();
        assertThat(status.hasClass("fa")).isEqualTo(true);
        switch (expectedStatus) {
        case SUCCESS:
            assertThat(status.hasClass("fa-check")).isEqualTo(true);
            break;
        case FAILURE:
            assertThat(status.hasClass("fa-ban")).isEqualTo(true);
            break;
        }
        assertThat(status.attr("title")).isEqualTo(expectedSeverity);
        Element ruleToggle = rule.getElementsByClass("rule-toggle").first();
        assertThat(ruleToggle).isNotNull();
        Element content = rule.getElementsByClass("content").first();
        assertThat(content).isNotNull();
    }

    protected final void verifyRuleResult(Document document, String id, String expectedColumnName, String... expectedValues) {
        Element ruleResult = document.getElementById("result(" + id + ")");
        Element thead = ruleResult.getElementsByTag("thead").first();
        assertThat(thead).isNotNull();
        Element th = thead.getElementsByTag("th").first();
        assertThat(th).isNotNull();
        assertThat(th.text()).isEqualTo(expectedColumnName);
        Element tbody = ruleResult.getElementsByTag("tbody").first();
        assertThat(tbody).isNotNull();
        Elements tds = tbody.getElementsByTag("td");
        List<String> values = tds.stream().map(td -> td.text()).collect(toList());
        assertThat(values).containsExactly(expectedValues);
    }

    private RuleSet getRuleSet(File ruleDirectory, String... adocFiles) throws RuleException {
        AsciidocRuleParserPlugin ruleParserPlugin = new AsciidocRuleParserPlugin();
        ruleParserPlugin.initialize();
        ruleParserPlugin.configure(RuleConfiguration.DEFAULT);
        RuleParser ruleParser = new RuleParser(Arrays.<RuleParserPlugin> asList(ruleParserPlugin));
        List<RuleSource> ruleSources = new ArrayList<>();
        for (String adocFile : adocFiles) {
            ruleSources.add(new FileRuleSource(new File(ruleDirectory, adocFile)));
        }
        return ruleParser.parse(ruleSources);
    }

    protected final void processRule(ReportPlugin plugin, Concept rule, Result<Concept> result) throws ReportException {
        plugin.beginConcept(rule);
        plugin.setResult(result);
        plugin.endConcept();
    }

    protected final void processRule(ReportPlugin plugin, Constraint rule, Result<Constraint> result) throws ReportException {
        plugin.beginConstraint(rule);
        plugin.setResult(result);
        plugin.endConstraint();
    }
}
