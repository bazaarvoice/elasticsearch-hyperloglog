# Elasticsearch HyperloglogPlus Aggregator

Simple aggregation which allows to aggregate HyperloglogPlus serialized objects saved as binary field in Elasticsearch

## Background

[HyperloglogPlus](https://en.wikipedia.org/wiki/HyperLogLog) datastructure allows us to compute cardinality of a multi set with a small trade-off in accuracy.
One of the interesting properties of Hyperloglog is that it allows to merge more than one Hyperloglog to compute total multi - set cardinality. 

This plugin uses HyperloglogPlus  implementation from [stream-lib](https://github.com/addthis/stream-lib) . We first considered Elasticsearch HLL implemenation, but decided
to go with stream-lib as ES implementation may change and usage of HyperLogLogPlus directly is not supported.

### Sample usecase

#### Unique audience count a dataset of product page visits , with a few billion users and 200 million products.

To get unique audience count by any dimension of given product ( name , description , attributes, brand etc etc ) , one of the ways is to index 
each profle as document with product and their attribute as property in document which  demands a huge index and the view is very 'visit centric'.

Approach used here is to index each product as document with a hyperloglog binary field which represents unique visitors for that product. Then, using 
*hyperlogsum* aggregation provided by this plugin - unique visitors can be computed in any dimension in product. 

Computing Hyperloglog binary field to index can be done by one of these ways:

1 - As explained in integration test, directly using stream-lib with same precision used by plugin internally 
( HyperUniqeSumAggregationBuilder.SERIALIZED_DENSE_PRECISION, HyperUniqeSumAggregationBuilder.SERIALIZED_SPARSE_PRECISION)

2 -  If you are using spark to index data to elasicsearch , UDAF in /spark folder might be useful which internally use same precision

3 - Custom UDAF for big data stack used ( hive / pig ) 

## Usage

### Elasticsearch Versions

Current master is compatible with ES 5.4.3 - latest ES production version. Please create an issue for your ES version specific verion of plugin

### Elasticsearch Mappings

Since we are using binary field in ES for storing HLL , doc_values should be set to True in mapping

### Indexing Example 

hll field should be mapped to type = binary and doc_values = True, like using an index template in below example

```
curl -XPUT http://localhost:9200/_template/template_1 -d '
{
    "order": 0,
    "template": "*",
    "mappings": {
      "product": {
        "properties": {
          "hll": {
	    "type": "binary",
	    "doc_values" : true
          },
	  "desc" : {
            "type" : "text",
            "fields" : {
              "keyword" : {
                "type" : "keyword"
              }
            }
	  }		
        }
      }
    },
    "aliases": {}
}'

```

python sample code to populate index  

```
import json
import requests

data = {
          "desc" : "bar",
          "hll" : "rO0ABXNyAFBjb20uY2xlYXJzcHJpbmcuYW5hbHl0aWNzLnN0cmVhbS5jYXJkaW5hbGl0eS5IeXBlckxvZ0xvZ1BsdXMkU2VyaWFsaXphdGlvbkhvbGRlct2sxediKNllDAAAeHB6AAACVf////4OGQHIAearF8aBOYiDAfAZlpgGrusBwO8M7ij6uSHKkgOA0RDW9Wuyig30qCvu9CTQ3yT62grKsRjumQeE9QG+vQ3c7ATc6TC0OsjSH/CaE6RY+PYD0I8WtvM1tMcBlswJ7vsQ0E/6/wLEvSWW/ibm4Q6YL9KADMyeCLq1JsDWKszxE7CzBIibCsrVCfS3CPCbG9TsA8TpVeaUCejZE9KlH4iPCNSNEczxB/pu+NUPutEC/KgVxpwBzKpI0poTwk6i6CO83APe/waKqRv+tg328CewmxLugwLqwA7GxwimiQWIswuC/i/WsxCo0Rrs2QbAywi2yATqkyz46SLO6wic/gXyGaTcAaiHMezAAvzfCrrwEsyUGbhHnKUDhLgj9IMSvogC7MMIrusBjPMD2L0FkvQPrKcfoJpM2r1K7rEGtP0S2rAr+tJM9oga3q8OivkJrPAF4uQojscWmMw62towntIEoN0DzowQjvAq7NYI4IQJ7sojsq0L5I8B+OoUptgohswMrNEa2Pw5iIMB5uEdurUmioM40M4CgrEj2OIKqIcPmosL7NkGvsMK5LIisOkH0PELuucEirAitKEH7NkG9JcBzPICnrAO8qEh0LkJkKoN0MIQ1pgLwu8XruUm3IQQ5rgVwt0RgJIwzK4DqMEw4IoZkO8KrqUP1uIIns4nyokO2kuorxPUr1OEvweWogL8qyDkpAyyig64shDqoS++zQvu4xbOuhL48grqohHg8QfSlQKO+wPmH6brQqSiCJSiAqSZVujUA9jDArqyC/T1Xng="
        }
        

url = 'http://localhost:9200/newindex/product/a'

for  i in range(1,100):
	requests.post(url + str(i),json = data)
```


### Querying with custom aggregation

```
curl -XPOST 'localhost:9200/_search?pretty=true&size=0' -d 
'{ "query" : {"match_all" : {}}, "aggs" : { "desc.keyword" : { "terms" : { "field" : "description"}  ,  "aggs" : {  "uniq" : {"hyperlogsum" : { "field" : "hll" } } } } } }'

{
  "took" : 3,
  "timed_out" : false,
  "_shards" : {
    "total" : 5,
    "successful" : 5,
    "failed" : 0
  },
  "hits" : {
    "total" : 100,
    "max_score" : 0.0,
    "hits" : [ ]
  },
  "aggregations" : {
    "desc.keyword" : {
      "doc_count_error_upper_bound" : 0,
      "sum_other_doc_count" : 0,
      "buckets" : [
        {
          "key" : "bar",
          "doc_count" : 100,
          "uniq" : {
            "value" : 200.0
          }
        }
      ]
    }
  }
}
```

## Setup

In order to install this plugin, you need to create a zip distribution first by running

```bash
gradle clean check
```

This will produce a zip file in `build/distributions`.

After building the zip file, you can install it like this

```bash
bin/plugin install file:///path/to/elasticsearch-hyperloglogplus/build/distribution/elasticsearch-hyperloglogplus.zip
```

## Bugs & TODO

* Use an encoding format for HLL ( Like REDIS ) 
* Consider LogLog Beta
* HLL Mapping Type 

( Thanks to [David Pilato's](https://github.com/dadoonet) gradle cookie cutter generator to make plugin projects easier )

