package org.elasticsearch.index.analysis;

import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequest;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.analysis.common.CommonAnalysisPlugin;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.plugin.analysis.combo.AnalysisComboPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

public class TestIntegration extends ESIntegTestCase {

    protected static final String INDEX = "some_index";
    protected static final String TYPE = "some_type";

    public static final String ANALYZER = "configured_analyzer";

    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(CommonAnalysisPlugin.class, AnalysisComboPlugin.class);
    }

    protected Settings nodeSettings(int nodeOrdinal) {
        Settings.Builder settings = Settings.builder()
                .put(super.nodeSettings(nodeOrdinal));
        return settings.build();
    }

    protected void assertAnalyzesTo(String analyzer, String input, String[] output, int startOffsets[], int endOffsets[], String types[], int position[]) {
        assertThat(output, notNullValue());
        AnalyzeResponse response = client().admin().indices().analyze(new AnalyzeRequest(INDEX).text(input).analyzer(analyzer)).actionGet();
        if (VERBOSE) {
            try {
                Map<String,String> params = new HashMap<String,String>();
                params.put("format", "text");
                logger.info("Tokens for \""+input+"\": " + response.toXContent(jsonBuilder().startObject(), new ToXContent.MapParams(params)).endObject().toString());
            } catch (IOException e) {
                logger.error("Tokens for \""+input+"\": ERROR", e);
            }
        }
        Iterator<AnalyzeResponse.AnalyzeToken> tokens = response.iterator();
        assertEquals("Token count should match exactly", output.length, response.getTokens().size());
        for (int i = 0; i < output.length; i++) {
            assertTrue("token "+i+" does not exist", tokens.hasNext());
            AnalyzeResponse.AnalyzeToken token = tokens.next();
            assertThat("term "+i, token.getTerm(), equalTo(output[i]));
            if (startOffsets != null)
                assertThat("startOffset "+i, token.getStartOffset(), equalTo(startOffsets[i]));
            if (endOffsets != null)
                assertThat("endOffset "+i, token.getEndOffset(), equalTo(endOffsets[i]));
            if (types != null)
                assertThat("type "+i, token.getType(), equalTo(types[i]));
            if (position != null) {
                assertThat("position "+i, token.getPosition(), equalTo(position[i]));
            }
        }
    }

    @Test
    public void testAnalysis() throws IOException {
        prepareCreate(INDEX)
                .setSettings(XContentFactory.jsonBuilder()
                    .startObject().startObject("index").startObject("analysis").startObject("analyzer")
                        .startObject(ANALYZER)
                            .field("type", "combo")
                            .startArray("sub_analyzers")
                                .value("whitespace")
                                .value("english")
                                .value("keyword")
                            .endArray()
                        .endObject()
                    .endObject().endObject().endObject().endObject()
                )
                .execute()
                .actionGet();
        ensureGreen(INDEX);

        assertAnalyzesTo(ANALYZER, "just a little test",
                new String[]{"just", "just", "just a little test", "a", "littl", "little", "test", "test"},
                new int[]{ 0,  0,  0,  5,  7,  7, 14, 14},
                new int[]{ 4,  4, 18,  6, 13, 13, 18, 18},
                null,
                new int[]{ 0,  0,  0,  1,  2,  2,  3,  3});
    }

    @Test
    public void testAnalysisWithCustomAnalyzersWithoutDuplication() throws IOException {
        prepareCreate(INDEX)
            .setSettings(XContentFactory.jsonBuilder()
                .startObject().startObject("index").startObject("analysis").startObject("analyzer")
                    .startObject("custom_analyzer_1")
                         .field("type", "custom")
                         .field("tokenizer", "standard")
                         .startArray("filter")
                             .value("lowercase")
                         .endArray()
                    .endObject()

                    .startObject("custom_analyzer_2")
                         .field("type", "custom")
                         .field("tokenizer", "standard")
                         .startArray("filter")
                             .value("asciifolding")
                         .endArray()
                    .endObject()

                    .startObject(ANALYZER)
                         .field("type", "combo")
                         .field("deduplication", true)
                         .startArray("sub_analyzers")
                             .value("custom_analyzer_1")
                             .value("custom_analyzer_2")
                         .endArray()
                    .endObject()
                .endObject().endObject().endObject().endObject()
            )
            .execute()
            .actionGet();
        ensureGreen(INDEX);

        assertAnalyzesTo(ANALYZER, "Ławka Kółko slowo",
            new String[]{"ławka", "Lawka", "kółko", "Kolko", "slowo"},
            new int[]{ 0,  0,  6,  6, 12},
            new int[]{ 5,  5, 11, 11, 17},
            null,
            new int[]{ 0,  0,  1,  1, 2});
    }

    @Test
    public void testAnalysisWithCustomAnalyzersWithDuplication() throws IOException {
        prepareCreate(INDEX)
            .setSettings(XContentFactory.jsonBuilder()
                .startObject().startObject("index").startObject("analysis").startObject("analyzer")
                    .startObject("custom_analyzer_1")
                        .field("type", "custom")
                        .field("tokenizer", "standard")
                    .startArray("filter")
                        .value("lowercase")
                    .endArray()
                .endObject()

                .startObject("custom_analyzer_2")
                    .field("type", "custom")
                    .field("tokenizer", "standard")
                    .startArray("filter")
                        .value("asciifolding")
                    .endArray()
                .endObject()

                .startObject(ANALYZER)
                    .field("type", "combo")
                    .field("deduplication", false)
                    .startArray("sub_analyzers")
                        .value("custom_analyzer_1")
                        .value("custom_analyzer_2")
                    .endArray()
                    .endObject()
                .endObject().endObject().endObject().endObject()
            )
            .execute()
            .actionGet();
        ensureGreen(INDEX);

        assertAnalyzesTo(ANALYZER, "Ławka Kółko slowo",
            new String[]{"ławka", "Lawka", "kółko", "Kolko", "slowo", "slowo"},
            new int[]{ 0,  0,  6,  6, 12, 12},
            new int[]{ 5,  5, 11, 11, 17, 17},
            null,
            new int[]{ 0,  0,  1,  1,  2,  2});
    }

    @Test
    public void testAnalysisShouldDeduplicateOnlyIfPositionIsTheSame() throws IOException {
        prepareCreate(INDEX)
            .setSettings(XContentFactory.jsonBuilder()
                .startObject().startObject("index").startObject("analysis").startObject("analyzer")
                    .startObject("custom_analyzer_1")
                         .field("type", "custom")
                         .field("tokenizer", "standard")
                         .startArray("filter")
                             .value("lowercase")
                         .endArray()
                    .endObject()

                    .startObject("custom_analyzer_2")
                         .field("type", "custom")
                         .field("tokenizer", "standard")
                         .startArray("filter")
                             .value("asciifolding")
                         .endArray()
                    .endObject()

                    .startObject(ANALYZER)
                         .field("type", "combo")
                         .field("deduplication", true)
                         .startArray("sub_analyzers")
                             .value("custom_analyzer_1")
                             .value("custom_analyzer_2")
                         .endArray()
                    .endObject()
                .endObject().endObject().endObject().endObject()
            )
            .execute()
            .actionGet();
        ensureGreen(INDEX);

        assertAnalyzesTo(ANALYZER, "Ławka Kółko slowo huh slowo",
            new String[]{"ławka", "Lawka", "kółko", "Kolko", "slowo", "huh", "slowo"},
            new int[]{ 0,  0,  6,  6, 12, 18, 22},
            new int[]{ 5,  5, 11, 11, 17, 21, 27},
            null,
            new int[]{ 0,  0,  1,  1,  2,  3, 4});
    }
}
