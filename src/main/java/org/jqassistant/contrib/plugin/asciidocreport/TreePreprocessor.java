package org.jqassistant.contrib.plugin.asciidocreport;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.buschmais.jqassistant.core.analysis.api.Result;
import com.buschmais.jqassistant.core.analysis.api.rule.ExecutableRule;
import com.buschmais.jqassistant.core.analysis.api.rule.Severity;
import com.buschmais.jqassistant.core.report.api.ReportContext;

import org.apache.commons.lang3.StringEscapeUtils;
import org.asciidoctor.ast.AbstractBlock;
import org.asciidoctor.ast.AbstractNode;
import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.Treeprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreePreprocessor extends Treeprocessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreePreprocessor.class);

    private final Map<String, RuleResult> conceptResults;
    private final Map<String, RuleResult> constraintResults;
    private final File reportDirectoy;
    private final ReportContext reportContext;

    public TreePreprocessor(Map<String, RuleResult> conceptResults, Map<String, RuleResult> constraintResults, File reportDirectory,
            ReportContext reportContext) {
        this.conceptResults = conceptResults;
        this.constraintResults = constraintResults;
        this.reportDirectoy = reportDirectory;
        this.reportContext = reportContext;
    }

    public Document process(Document document) {
        DocumentParser documentParser = DocumentParser.parse(document);
        enrichResults(documentParser.getConceptBlocks(), conceptResults);
        enrichResults(documentParser.getConstraintBlocks(), constraintResults);
        return document;
    }

    private void enrichResults(Map<String, AbstractBlock> blocks, Map<String, RuleResult> results) {
        for (Map.Entry<String, AbstractBlock> blockEntry : blocks.entrySet()) {
            String id = blockEntry.getKey();
            AbstractBlock block = blockEntry.getValue();
            RuleResult result = results.get(id);
            List<String> content = renderRuleResult(result);
            AbstractNode parent = block.getParent();
            List<AbstractBlock> siblings = ((AbstractBlock) parent).getBlocks();
            int i = siblings.indexOf(block);
            siblings.add(i + 1, createBlock((AbstractBlock) parent, "paragraph", content, new HashMap<String, Object>(), new HashMap<>()));
        }
    }

    /**
     * Renders a {@link RuleResult} to HTML.
     *
     * @param result
     *            The {@link RuleResult}.
     * @return The HTML to be embedded into the document.
     */
    private List<String> renderRuleResult(RuleResult result) {
        List<String> content = new ArrayList<>();
        if (result != null) {
            ExecutableRule<?> rule = result.getRule();
            content.add("<div id=\"result(" + rule.getId() + ")\">");
            Result.Status status = result.getStatus();
            content.add("<div class=\"paragraph\">");
            content.add("<p>");
            content.add(renderStatusContent(status));
            Severity severity = rule.getSeverity();
            content.add("Severity: " + severity.getInfo(result.getEffectiveSeverity()));
            content.add("</p>");
            content.add("</div>");
            List<ReportContext.Report<?>> reports = reportContext.getReports(rule);
            if (!reports.isEmpty()) {
                for (ReportContext.Report<?> report : reports) {
                    switch (report.getReportType()) {
                    case IMAGE:
                        content.add(renderImage(getReportUrl(report)));
                        break;
                    case LINK:
                        content.add(renderLink(getReportUrl(report), report.getLabel()));
                        break;
                    }
                }
            } else {
                content.add(renderResultTable(result));
            }
            content.add("</div>");
        } else {
            content.add("Status: Not Available");
        }
        return content;
    }

    private String getReportUrl(ReportContext.Report<?> report) {
        URL url = report.getUrl();
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            return getRelativeReportUrl(url);
        }
        return url.toExternalForm();
    }

    private String getRelativeReportUrl(URL url) {
        Path path;
        try {
            path = Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            LOGGER.warn("Cannot determine path from URL '" + url + "'.", e);
            return url.toExternalForm();
        }
        Path relativePath = reportDirectoy.getAbsoluteFile().toPath().relativize(path);
        return relativePath.toString().replace('\\', '/');
    }

    private String renderLink(String url, String label) {
        StringBuilder a = new StringBuilder();
        a.append("<a href=").append('"').append(url).append('"').append(">");
        a.append(label);
        a.append("</a>");
        return a.toString();
    }

    private String renderStatusContent(Result.Status status) {
        return "Status: " + "<span class=\"" + StatusHelper.getStatusColor(status) + "\">" + status.toString() + "</span>";
    }

    /**
     * Renders a {@link RuleResult }as table.
     *
     * @param result
     *            The {@link RuleResult}.
     * @return The rendered table.
     */
    private String renderResultTable(RuleResult result) {
        List<String> columnNames = result.getColumnNames();
        StringBuilder tableBuilder = new StringBuilder();
        tableBuilder.append("<table>").append('\n');
        tableBuilder.append("<thead>").append('\n');
        tableBuilder.append("<tr>").append('\n');
        for (String columnName : columnNames) {
            tableBuilder.append("<th>").append(columnName).append("</th>").append('\n');
        }
        tableBuilder.append("</tr>").append('\n');
        tableBuilder.append("</thead>").append('\n');
        tableBuilder.append("<tbody>").append('\n');
        for (Map<String, List<String>> row : result.getRows()) {
            tableBuilder.append("<tr>").append('\n');
            for (String columnName : columnNames) {
                tableBuilder.append("<td>").append('\n');
                for (String value : row.get(columnName)) {
                    tableBuilder.append(StringEscapeUtils.escapeHtml4(value)).append('\n');
                }
                tableBuilder.append("</td>").append('\n');
            }
            tableBuilder.append("</tr>").append('\n');
        }
        tableBuilder.append("</tbody>").append('\n');
        tableBuilder.append("</table>").append('\n');
        return tableBuilder.toString();
    }

    /**
     * Embed an image with the given file name.
     *
     * @param url
     *            The {@link URL} of the image to embed.
     * @return The HTML to be embedded in the document.
     */
    private String renderImage(String url) {
        StringBuilder content = new StringBuilder();
        content.append("<div>");
        content.append("<a href=\"" + url + "\">");
        content.append("<img src=\"" + url + "\"/>");
        content.append("</a>");
        content.append("</div>");
        return content.toString();
    }
}
