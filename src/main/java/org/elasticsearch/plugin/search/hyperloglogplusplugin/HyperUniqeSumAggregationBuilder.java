package org.elasticsearch.plugin.search.hyperloglogplusplugin;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregatorFactories.Builder;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.support.*;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;

public class HyperUniqeSumAggregationBuilder extends ValuesSourceAggregationBuilder<ValuesSource.Bytes, HyperUniqeSumAggregationBuilder> {
    public static final String NAME = "hyperlogsum";
    public static final int SERIALIZED_SPARSE_PRECISION  = 25;
    public static final int SERIALIZED_DENSE_PRECISION = 14;


    private static final ObjectParser<HyperUniqeSumAggregationBuilder, QueryParseContext> PARSER;

    static {
        PARSER = new ObjectParser<>(HyperUniqeSumAggregationBuilder.NAME);
        ValuesSourceParserHelper.declareBytesFields(PARSER, false, false);
    }

    public static AggregationBuilder parse(String aggregationName, QueryParseContext context) throws IOException {
        return PARSER.parse(context.parser(), new HyperUniqeSumAggregationBuilder(aggregationName), context);
    }

    public HyperUniqeSumAggregationBuilder(String name) {
        super(name, ValuesSourceType.BYTES, ValueType.STRING);
    }

    public HyperUniqeSumAggregationBuilder(StreamInput in) throws IOException {
        super(in, ValuesSourceType.BYTES, ValueType.STRING);
    }

    @Override
    protected void innerWriteTo(StreamOutput streamOutput) throws IOException {
        //noop
    }

    @Override
    protected HyperUniqueSumAggregatorFactory innerBuild(SearchContext context, ValuesSourceConfig<ValuesSource.Bytes> config,
                                                         AggregatorFactory<?> parent, Builder subFactoriesBuilder) throws IOException {
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
