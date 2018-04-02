package org.elasticsearch.plugin.search.hyperloglogplusplugin;

import java.io.IOException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.support.ValueType;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.support.ValuesSourceParserHelper;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.search.internal.SearchContext;

public class HyperUniqueSumAggregationBuilder extends ValuesSourceAggregationBuilder<ValuesSource.Bytes, HyperUniqueSumAggregationBuilder> {
    public static final String NAME = "hyperlogsum";
    public static final int SERIALIZED_SPARSE_PRECISION  = 25;
    public static final int SERIALIZED_DENSE_PRECISION = 14;

    private static final ObjectParser<HyperUniqueSumAggregationBuilder, Void> PARSER;
    static {
        PARSER = new ObjectParser<>(HyperUniqueSumAggregationBuilder.NAME);
        ValuesSourceParserHelper.declareBytesFields(PARSER, true, false);
    }

    public static AggregationBuilder parse(String aggregationName, XContentParser parser) throws IOException {
        return PARSER.parse(parser, new HyperUniqueSumAggregationBuilder(aggregationName), null);
    }

    public HyperUniqueSumAggregationBuilder(String name) {
        super(name, ValuesSourceType.BYTES, ValueType.STRING);
    }

    public HyperUniqueSumAggregationBuilder(StreamInput in) throws IOException {
        super(in, ValuesSourceType.BYTES, ValueType.STRING);
    }

    @Override
    protected void innerWriteTo(StreamOutput out) throws IOException {
        /*no fields*/
    }

    @Override
    protected HyperUniqueSumAggregatorFactory innerBuild(SearchContext context, ValuesSourceConfig<ValuesSource.Bytes> config,
                    AggregatorFactory<?> parent, AggregatorFactories.Builder subFactoriesBuilder) throws IOException {
        return new HyperUniqueSumAggregatorFactory(name, config, context, parent, subFactoriesBuilder, metaData);
    }

    @Override
    public XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        return builder;
    }

    @Override
    protected int innerHashCode() {
        return 0;
    }

    @Override
    protected boolean innerEquals(Object obj) {
        return true;
    }

    @Override
    public String getType() {
        return NAME;
    }
}
