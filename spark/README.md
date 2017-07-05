# Spark UDAF to write Hyperloglog byte array

1 - Reference [stream-lib](https://mvnrepository.com/artifact/com.clearspring.analytics/stream/2.9.5) in your build configuration.

2 - Copy spark UDAF code [hyperloglogUDAF.scala](./hyperloglogUDAF.scala) into your spark code.  This UDAF can be used to collect values to a `Hyperloglog` structure.

### Example
```scala
val hll = new HyperLogPlusPlusAgg()

val visitorHLL = pageViews
        .groupBy($"product")
        .agg(hll($"VisitorID").as("HLL"))
        .toDF()

```

 This dataframe will now contain "HLL" field as a Base64 encoded String representation of the serialized HyperLogLogPlus object which will be constructed
 from each VisitorID.  This can now be indexed into Elasticsearch as usual: by using the [elasticsearch-spark](https://github.com/elastic/elasticsearch-hadoop#apache-spark) library.
 
```scala
 import org.elasticsearch.spark.sql._
 
 df.saveToEs(...)
 
```