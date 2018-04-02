package org.elasticsearch.plugin.search.hyperloglogplusplugin;

import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorFactory;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class HyperUniqueSumAggregatorFactory extends ValuesSourceAggregatorFactory<ValuesSource.Bytes, HyperUniqueSumAggregatorFactory> {

    public HyperUniqueSumAggregatorFactory(String name, ValuesSourceConfig<ValuesSource.Bytes> config, SearchContext context,
                                           AggregatorFactory<?> parent, AggregatorFactories.Builder subFactoriesBuilder,
                                           Map<String, Object> metaData) throws IOException {
        super(name, config, context, parent, subFactoriesBuilder, metaData);

    }

    @Override
    protected Aggregator createUnmapped(Aggregator parent, List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData)
            throws IOException {
        return new HyperUniqueSumAggregator(name, null, config.format(), context, parent, pipelineAggregators, metaData);
    }

    @Override
    protected Aggregator doCreateInternal(ValuesSource.Bytes valuesSource, Aggregator parent, boolean collectsFromSingleBucket,
                                          List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData) throws IOException {
        return new HyperUniqueSumAggregator(name, valuesSource, config.format(), context, parent, pipelineAggregators, metaData);
    }
}
