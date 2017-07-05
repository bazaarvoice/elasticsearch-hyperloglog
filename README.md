# Elasticsearch HyperloglogPlus Aggregator

Simple aggregation which allows the aggregation of HyperloglogPlus serialized objects which have been saved as a binary field in Elasticsearch.

## Background

The [HyperloglogPlus](https://en.wikipedia.org/wiki/HyperLogLog) data structure allows us to compute the cardinality of a multiset with a small trade-off in accuracy.
One of the interesting properties of Hyperloglog is that it allows to merge more than one Hyperloglog to compute total multiset cardinality.

This plugin uses a HyperloglogPlus implementation from [stream-lib](https://github.com/addthis/stream-lib).  We first considered Elasticsearch HLL implemenation, but decided
to go with stream-lib as ES implementation may change and usage of HyperLogLogPlus directly is not supported.

### Sample Use Case

#### Unique audience count

Given a data set of product page visits with a few billion users and 200 million products, we must count the distinct count of users who had seen a combination of
products.  These products can be determined by any dimension of given product (name, description, attributes, brand, etc.).  One approach would be to index
each profile as a document with products and their attributes as properties in the document.  This demands a huge index and the view is very 'visit centric'.

Instead, the approach used here is to index each product as a document with a hyperloglog binary field representing unique visitors for that product. Then, using the
`hyperlogsum` aggregation provided by this plugin, unique visitors can be computed by any product dimension.

Computing Hyperloglog binary field to index can be done by one of these ways:

1 - As demonstrated in the [integration tests](./src/test/java/org/elasticsearch/plugin/search/hyperloglogplusplugin/HyperLogLogPlusAggregationPluginTests.java),
directly using stream-lib with same precision used by plugin internally (`HyperUniqeSumAggregationBuilder.SERIALIZED_DENSE_PRECISION`, `HyperUniqeSumAggregationBuilder.SERIALIZED_SPARSE_PRECISION`)

2 - If using spark to index data into elasticsearch, an example [UDAF](/spark/hyperloglogUDAF.scala) has been provided which internally uses the same precision.

3 - One could also build a custom UDAF for your stack, modeled on the spark version (feel free to contribute back!)

## Usage

### Elasticsearch Versions

Current master is compatible with ES 5.4.3 - latest ES production version.  Please create an issue if a different ES-version-specific release needs to be made.

### Elasticsearch Mappings

Since we are using binary field in ES for storing HLL, `doc_values` should be set to `true` in the mapping declaration.

### Indexing Example 

hll field should be mapped to `type = binary` and `doc_values = true`, like using an index template in below example

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
          "doc_values": true
        },
        "desc": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword"
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

```python
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

## Acknowledgement

Thanks to [David Pilato's](https://github.com/dadoonet) gradle cookie cutter generator to make plugin projects easier.

