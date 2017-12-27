package org.elasticsearch.plugin.search.hyperloglogplusplugin;

import com.clearspring.analytics.stream.cardinality.CardinalityMergeException;
import com.clearspring.analytics.stream.cardinality.HyperLogLogPlus;
import java.util.Arrays;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticsearchGenerationException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.metrics.InternalNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class InternalHyperUniqueSum extends InternalNumericMetricsAggregation.SingleValue {
    private final byte[] hyperLogLogPlusBytes;

    InternalHyperUniqueSum(String name, byte[] hyperLogLogPlusBytes, DocValueFormat formatter, List<PipelineAggregator> pipelineAggregators,
                           Map<String, Object> metaData) {
        super(name, pipelineAggregators, metaData);
        this.hyperLogLogPlusBytes = hyperLogLogPlusBytes;
        this.format = formatter;
    }


    public InternalHyperUniqueSum(StreamInput in) throws IOException {
        super(in);
        format = in.readNamedWriteable(DocValueFormat.class);
        hyperLogLogPlusBytes = in.readByteArray();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(format);
        out.writeByteArray(hyperLogLogPlusBytes);
    }

    @Override
    public String getWriteableName() {
        return HyperUniqueSumAggregationBuilder.NAME;
    }

    @Override
    public double value() {
        return HyperUniqueSumAggregator.deserializeHyperLogLogPlus(new BytesRef(hyperLogLogPlusBytes)).cardinality();
    }


    @Override
    public InternalHyperUniqueSum doReduce(List<InternalAggregation> aggregations, ReduceContext reduceContext) {
        HyperLogLogPlus total = new HyperLogLogPlus(HyperUniqueSumAggregationBuilder.SERIALIZED_DENSE_PRECISION, HyperUniqueSumAggregationBuilder.SERIALIZED_SPARSE_PRECISION);
        for (InternalAggregation aggregation : aggregations) {
            byte[] bytes = ((InternalHyperUniqueSum) aggregation).hyperLogLogPlusBytes;
            if (bytes != null && bytes.length > 0) {
                HyperLogLogPlus current = HyperUniqueSumAggregator.deserializeHyperLogLogPlus(new BytesRef(bytes));

                if (current != null) {
                    try {
                        total = (HyperLogLogPlus) total.merge(current);
                    } catch (CardinalityMergeException cme) {
                        throw new ElasticsearchGenerationException("Failed to merge HLL+ ", cme);

                    }
                }
            }

        }
        return new InternalHyperUniqueSum(name, HyperUniqueSumAggregator.serializeHyperLogLogPlus(total).bytes, format, pipelineAggregators(), getMetaData());
    }

    @Override
    public XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        builder.field(CommonFields.VALUE.getPreferredName(), value());
        if (format != DocValueFormat.RAW) {
            builder.field(CommonFields.VALUE_AS_STRING.getPreferredName(), format.format(value()));
        }
        return builder;
    }

    @Override
    public boolean doEquals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        final InternalHyperUniqueSum that = (InternalHyperUniqueSum) o;

        return Arrays.equals(hyperLogLogPlusBytes, that.hyperLogLogPlusBytes);
    }

    @Override
    public int doHashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(hyperLogLogPlusBytes);
        return result;
    }
}
