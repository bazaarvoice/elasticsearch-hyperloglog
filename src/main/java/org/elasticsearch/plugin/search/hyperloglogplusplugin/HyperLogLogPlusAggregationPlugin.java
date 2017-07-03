package org.elasticsearch.plugin.search.hyperloglogplusplugin;

import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SearchPlugin;


import java.util.ArrayList;
import java.util.List;


public class HyperLogLogPlusAggregationPlugin extends Plugin implements SearchPlugin {

    @Override
    public List<AggregationSpec> getAggregations() {
        ArrayList<AggregationSpec> aggregationSpecs = new ArrayList<>(1);
        aggregationSpecs.add(new AggregationSpec(HyperUniqeSumAggregationBuilder.NAME, HyperUniqeSumAggregationBuilder::new, HyperUniqeSumAggregationBuilder::parse)
                .addResultReader(InternalHyperUniqueSum::new));
        return aggregationSpecs;
    }



}
