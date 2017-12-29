package org.elasticsearch.plugin.search.hyperloglogplusplugin;


import com.clearspring.analytics.stream.cardinality.HyperLogLogPlus;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.plugins.Plugin;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.test.ESIntegTestCase;

import java.util.*;

@ESIntegTestCase.SuiteScopeTestCase
public class HyperLogLogPlusAggregationPluginTests extends ESIntegTestCase {

    // adds string representations of integers 0 .. max-1
    private String getHLLStringForTesting(int max) {
        HyperLogLogPlus hyperLogLogPlus = new HyperLogLogPlus(HyperUniqueSumAggregationBuilder.SERIALIZED_DENSE_PRECISION, HyperUniqueSumAggregationBuilder.SERIALIZED_SPARSE_PRECISION);
        for ( int i = 0; i < max ; i ++){
            hyperLogLogPlus.offer(Integer.toString(i));
        }
        return  Base64.getEncoder().encodeToString(HyperUniqueSumAggregator.serializeHyperLogLogPlus(hyperLogLogPlus).bytes);
    }

    private String getHLLStringForTestingWithWrongPrecision(int max) {
        HyperLogLogPlus hyperLogLogPlus = new HyperLogLogPlus(HyperUniqueSumAggregationBuilder.SERIALIZED_DENSE_PRECISION + 5, HyperUniqueSumAggregationBuilder.SERIALIZED_SPARSE_PRECISION + 5);
        for ( int i = 0; i < max ; i ++){
            hyperLogLogPlus.offer(Integer.toString(i));
        }
        return  Base64.getEncoder().encodeToString(HyperUniqueSumAggregator.serializeHyperLogLogPlus(hyperLogLogPlus).bytes);
    }

    @Override
    public void setupSuiteScopeCluster() throws Exception {

        List<IndexRequestBuilder> builders ;

        prepareCreate("idx_no_hll")
                .addMapping("type", "tag", "type=keyword", "hll", "type=binary,doc_values=true")
                .execute()
                .actionGet();

        prepareCreate("idx_hll")
                .addMapping("type", "tag", "type=keyword", "hll", "type=binary,doc_values=true")
                .execute()
                .actionGet();

        prepareCreate("idx_invalid_hll")
                .addMapping("type", "tag", "type=keyword", "hll", "type=binary,doc_values=true")
                .execute()
                .actionGet();

        prepareCreate("idx_wrong_precision_hll")
                .addMapping("type", "tag", "type=keyword", "hll", "type=binary,doc_values=true")
                .execute()
                .actionGet();

        builders = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            builders.add(client().prepareIndex("idx_no_hll", "type", ""+i).setSource(jsonBuilder()
                    .startObject()
                    .field("value", i*2)
                    .endObject()));
        }
        indexRandom(true, builders);
        builders = new ArrayList<>();

        List<String> hllEntries = new ArrayList<String>(Arrays.asList("fred", "barney", "wilma"));
        List<String> tags = new ArrayList<String>(Arrays.asList("crazy","mayBeCrazy"));

        // First document will contain 100 unique values
        // second document will same values in first document + 10 additional unique values
        // total unique values in index should be 110
        for (int i = 0; i < 2; i++) {
            builders.add(client().prepareIndex("idx_hll", "type", "" + i).setSource(jsonBuilder()
                    .startObject()
                    .field("tag", tags.get(i))
                    .field("hll", getHLLStringForTesting(100 + i*10 ))
                    .endObject()));
        }
        indexRandom(true, builders);

        builders = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            builders.add(client().prepareIndex("idx_invalid_hll", "type", ""+i).setSource(jsonBuilder()
                    .startObject()
                    .field("value", i * 2)
                    .field("hll",Base64.getEncoder().encode("invalid hll string".getBytes()))
                    .endObject()));
        }
        indexRandom(true, builders);

        builders = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            builders.add(client().prepareIndex("idx_wrong_precision_hll", "type", ""+i).setSource(jsonBuilder()
                    .startObject()
                    .field("value", i * 2)
                    .field("hll", getHLLStringForTestingWithWrongPrecision(100 + i*10 ))
                    .endObject()));
        }
        indexRandom(true, builders);

        ensureSearchable();
    }

    public void testEmptyAggregation() throws Exception {

        SearchResponse searchResponse = client().prepareSearch("idx_no_hll")
                .setQuery(matchAllQuery())
                .addAggregation(new HyperUniqueSumAggregationBuilder("hyperlog").field("hll"))
                .execute().actionGet();

        assertThat(searchResponse.getHits().getTotalHits(), equalTo(2L));
        NumericMetricsAggregation.SingleValue numericMetricsAggregation = searchResponse.getAggregations().get("hyperlog");
        assertThat(numericMetricsAggregation, notNullValue());
        assertEquals("expected 0.0 ", "0.0", numericMetricsAggregation.getValueAsString());

    }

    public void testUniqueSum1() throws Exception {

        SearchResponse searchResponse = client().prepareSearch("idx_hll")
                .setQuery(matchAllQuery())
                .addAggregation(new HyperUniqueSumAggregationBuilder("hyperlog").field("hll"))
                .execute().actionGet();

        assertThat(searchResponse.getHits().getTotalHits(), equalTo(2L));
        NumericMetricsAggregation.SingleValue numericMetricsAggregation = searchResponse.getAggregations().get("hyperlog");
        assertThat(numericMetricsAggregation, notNullValue());
        assertThat(numericMetricsAggregation.value(), equalTo(110.0));

    }

    public void testUniqueSum2() throws Exception {

        SearchResponse searchResponse = client().prepareSearch("idx_hll")
                .setQuery(termQuery("tag", "crazy"))
                .addAggregation(new HyperUniqueSumAggregationBuilder("hyperlog").field("hll"))
                .execute().actionGet();

        assertThat(searchResponse.getHits().getTotalHits(), equalTo(1L));
        NumericMetricsAggregation.SingleValue numericMetricsAggregation = searchResponse.getAggregations().get("hyperlog");
        assertThat(numericMetricsAggregation, notNullValue());
        assertThat(numericMetricsAggregation.value(), equalTo(100.0));

    }

    public void testInBuckets() throws Exception {

        SearchResponse searchResponse = client().prepareSearch("idx_hll")
                .setQuery(matchAllQuery())
                .addAggregation(AggregationBuilders.terms("tag")
                        .field("tag")
                        .subAggregation(new HyperUniqueSumAggregationBuilder("hyperlog").field("hll")))
                        .execute().actionGet();

        assertThat(searchResponse.getHits().getTotalHits(), equalTo(2L));
        StringTerms stringTerms = searchResponse.getAggregations().get("tag");
        assertThat(stringTerms.getBuckets().size(), equalTo(2));
        NumericMetricsAggregation.SingleValue numericMetricsAggregation = stringTerms.getBucketByKey("crazy").getAggregations().get("hyperlog");
        assertThat(numericMetricsAggregation.value(), equalTo(100.0));
        numericMetricsAggregation = stringTerms.getBucketByKey("mayBeCrazy").getAggregations().get("hyperlog");
        assertThat(numericMetricsAggregation.value(), equalTo(110.0));

    }

    public void testInvalidHLL() throws Exception {

        try {
            SearchResponse searchResponse = client().prepareSearch("idx_invalid_hll")
                    .setQuery(matchAllQuery())
                    .addAggregation(new HyperUniqueSumAggregationBuilder("hyperlog").field("hll"))
                    .execute().actionGet();

        }catch (Exception ex){
            assertThat(ex.toString(),containsString("Failed to deserialize HLLPlus"));
        }

    }


    @Override
    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return Collections.singletonList(HyperLogLogPlusAggregationPlugin.class);
    }

    @Override
    protected Collection<Class<? extends Plugin>> getMockPlugins(){
        ArrayList<Class<? extends Plugin>> mocks = new ArrayList<>(super.getMockPlugins());
        mocks.add(HyperLogLogPlusAggregationPlugin.class);
        return mocks;
    }

}

