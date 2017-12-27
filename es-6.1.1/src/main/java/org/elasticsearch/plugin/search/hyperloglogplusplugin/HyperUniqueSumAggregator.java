package org.elasticsearch.plugin.search.hyperloglogplusplugin;

import com.clearspring.analytics.stream.cardinality.CardinalityMergeException;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticsearchGenerationException;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.util.BigArrays;
import com.clearspring.analytics.stream.cardinality.HyperLogLogPlus;

import org.elasticsearch.common.util.ObjectArray;
import org.elasticsearch.index.fielddata.SortedBinaryDocValues;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.LeafBucketCollector;
import org.elasticsearch.search.aggregations.LeafBucketCollectorBase;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregator;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.internal.SearchContext;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class HyperUniqueSumAggregator extends NumericMetricsAggregator.SingleValue {

    private final ValuesSource valuesSource;
    private final DocValueFormat format;
    private ObjectArray<HyperLogLogPlus> hyperLogLogPlusPlusObjectArray;

    HyperUniqueSumAggregator(String name, ValuesSource valuesSource, DocValueFormat formatter, SearchContext context,
                             Aggregator parent, List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData) throws IOException {
        super(name, context, parent, pipelineAggregators, metaData);
        this.valuesSource = valuesSource;
        this.format = formatter;
        if (valuesSource != null) {
            hyperLogLogPlusPlusObjectArray = context.bigArrays().newObjectArray(1);
        }
    }

    public static BytesRef serializeHyperLogLogPlus(Serializable obj) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(512);
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(byteArrayOutputStream);
            out.writeObject(obj);
        } catch (IOException e) {
            throw new ElasticsearchGenerationException("Failed to serialize HLLPlus ", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new RuntimeException("Exception on closing HLLPlus output stream  ", e);
                }
            }
        }
        return new BytesRef(byteArrayOutputStream.toByteArray());
    }

    public static HyperLogLogPlus deserializeHyperLogLogPlus(BytesRef bytesRef) {
        byte[] bytesToDeserialize = Arrays.copyOfRange(bytesRef.bytes, bytesRef.offset, bytesRef.offset + bytesRef.length);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytesToDeserialize);
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(byteArrayInputStream);
            if (in == null) {
                return null;
            }
            return (HyperLogLogPlus) in.readObject();
        } catch (Exception e) {
            throw new ElasticsearchGenerationException("Failed to deserialize HLLPlus ", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new RuntimeException("Exception on closing HLLPlus stream  ", e);
                }
            }
        }

    }

    @Override
    public boolean needsScores() {
        return false;
    }

    @Override
    public LeafBucketCollector getLeafCollector(LeafReaderContext ctx,
                                                final LeafBucketCollector sub) throws IOException {
        if (valuesSource == null) {
            return LeafBucketCollector.NO_OP_COLLECTOR;
        }
        final BigArrays bigArrays = context.bigArrays();
        final SortedBinaryDocValues values = valuesSource.bytesValues(ctx);
        return new LeafBucketCollectorBase(sub, values) {
            @Override
            public void collect(int doc, long bucket) throws IOException {
                hyperLogLogPlusPlusObjectArray = bigArrays.grow(hyperLogLogPlusPlusObjectArray, bucket + 1);
                values.advanceExact(doc);
                final int valuesCount = values.docValueCount();
                HyperLogLogPlus hll;
                for (int i = 0; i < valuesCount; i++) {
                    hll = deserializeHyperLogLogPlus(values.nextValue());
                    HyperLogLogPlus current = hyperLogLogPlusPlusObjectArray.get(bucket);
                    if (current == null) {
                        hyperLogLogPlusPlusObjectArray.set(bucket, hll);
                    } else {
                        try {
                            hyperLogLogPlusPlusObjectArray.set(bucket, (HyperLogLogPlus) hll.merge(current));
                        } catch (CardinalityMergeException cme) {
                            throw new ElasticsearchGenerationException("Failed to merge HyperLogLogPlus structures  ", cme);
                        }
                    }
                }
            }
        };
    }

    @Override
    public InternalAggregation buildAggregation(long bucket) throws IOException {
        if (valuesSource == null || bucket >= hyperLogLogPlusPlusObjectArray.size()) {
            return buildEmptyAggregation();
        }

        BytesRef bytesRefToSerialize = HyperUniqueSumAggregator.serializeHyperLogLogPlus(hyperLogLogPlusPlusObjectArray.get(bucket));
        return new InternalHyperUniqueSum(name, bytesRefToSerialize.bytes, format, pipelineAggregators(), metaData());
    }

    @Override
    public InternalAggregation buildEmptyAggregation() {
        return new InternalHyperUniqueSum(name, new BytesRef().bytes, format, pipelineAggregators(), metaData());
    }

    @Override
    public void doClose() {
        Releasables.close(hyperLogLogPlusPlusObjectArray);
    }

    @Override
    public double metric(long owningBucketOrd) {
        return hyperLogLogPlusPlusObjectArray.get(owningBucketOrd).cardinality();
    }
}