# Spark UDAF to write Hyperloglog byte array

1  - Refer (stream-lib)[https://mvnrepository.com/artifact/com.clearspring.analytics/stream/2.9.5]  in your build configuration
2  - Copy spark UDAF code (hyperloglogUDAF.scala ) into your spark code . This UDAF can be used to collect values to a Hyperloglog structure.

### Example

val hll = new HyperLogPlusPlusAgg()

val visitorHLL = pageViews
        .groupBy($"product")
        .agg(hll($"VisitorID").as("HLL"))
        .toDF()
        
 This dataframe will contain "HLL" field as Base64 encoded String representation of serialized HyperLogLogPlus object which will be constructed 
 from each VisitorID. which can be indexed to Elasticsearch using elasticsearch-spark library 
 
 import org.elasticsearch.spark.sql._
 
 df.saveToEs(...)
 
 