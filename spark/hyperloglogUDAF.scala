import java.io._
import java.util.Base64

import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.Row
import org.apache.spark.sql.types._
import com.clearspring.analytics.stream.cardinality.HyperLogLogPlus
/**
 * UDAF which builds HLL Plus from String Column Values and returns
 * serialized HLL Object encoded as Base64 String
 *
 * Uses stream-lib HLL Plus implementation
 */
class HyperLogPlusPlusAgg extends UserDefinedAggregateFunction {

  @throws(classOf[IOException])
  def serializeHLL(obj: Object): Array[Byte] = {
    val baos: ByteArrayOutputStream = new ByteArrayOutputStream(512)
    var out: ObjectOutputStream = null
    try {
      out = new ObjectOutputStream(baos)
      out.writeObject(obj)
    } finally {
      if (out != null) {
        out.close
      }
    }
    return baos.toByteArray
  }

  @throws(classOf[ClassNotFoundException])
  @throws(classOf[IOException])
  def deserializeHLL(bytes: Array[Byte]): HyperLogLogPlus = {
    val bais: ByteArrayInputStream = new ByteArrayInputStream(bytes)
    var in: ObjectInputStream = null
    try {
      in = new ObjectInputStream(bais)
      return in.readObject.asInstanceOf[HyperLogLogPlus]
    } finally {
      if (in != null) {
        in.close
      }
    }
  }
  // input can be any type , but we use String , probably we can make this generic and test
  override def inputSchema: org.apache.spark.sql.types.StructType =
    StructType(StructField("value", StringType) :: Nil)

  // Internal Fields to keep aggregate
  override def bufferSchema: StructType = StructType(
    StructField("hllbits", BinaryType) :: Nil
  )

  // output will be Base64 encoded HLL Byte Array
  override def dataType: DataType = StringType

  override def deterministic: Boolean = true

  // initializing hll buffer with empty HLL
  override def initialize(buffer: MutableAggregationBuffer): Unit = {
    val hll = new HyperLogLogPlus(14, 25)
    buffer(0) = serializeHLL(hll)
  }

  // update hll with value from input column and deserialize back to buffer
  override def update(buffer: MutableAggregationBuffer, input: Row): Unit = {
    val hll = new HyperLogLogPlus(14, 25)
    hll.offer(input.getAs[String](0))
    buffer(0) = serializeHLL(hll)
  }

  // merge HLLs to buffer
  override def merge(buffer1: MutableAggregationBuffer, buffer2: Row): Unit = {
    val mergedHLL = deserializeHLL(buffer1.getAs[Array[Byte]](0))
      .merge(deserializeHLL(buffer2.getAs[Array[Byte]](0)))
    buffer1(0) = serializeHLL(mergedHLL)
  }

  // Convert serialized HLL from buffer  to Base64
  override def evaluate(buffer: Row): Any = {
    new String(Base64.getEncoder.encode(buffer.getAs[Array[Byte]](0)))
  }
}
