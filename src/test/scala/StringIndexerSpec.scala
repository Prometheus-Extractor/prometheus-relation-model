import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import se.lth.cs.docforia.Document
import se.lth.cs.docforia.memstore.MemoryDocument
import se.lth.cs.docforia.graph.text.{Sentence, Token}

import scala.collection.JavaConverters._
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.log4j.{Level, Logger}
import com.holdenkarau.spark.testing.SharedSparkContext
import com.sony.prometheus.stages.StringIndexer
import com.sony.prometheus.stages.StringIndexer
import com.sony.prometheus.utils.Filters

class StringIndexerSpec extends FlatSpec with BeforeAndAfter with Matchers with SharedSparkContext {

  trait TestDocument {
    val stringDoc = """
    Apache Spark's StringIndexer is inferior to TokenEncoder!
    Apache Spark's StringIndexer is inferior to TokenEncoder!
    Apache Spark's StringIndexer is inferior to TokenEncoder!
    Apache Spark's StringIndexer is inferior to TokenEncoder!
    Apache Spark's StringIndexer is inferior to TokenEncoder!
    """
    val mDoc: Document = new MemoryDocument(stringDoc)
    // build Token:s
    val words = stringDoc.split("\\s+")
    val tokens = words.map(w => (stringDoc.indexOfSlice(w), w.length)).map(idxPair => {
      new Token(mDoc).setRange(idxPair._1, idxPair._1 + idxPair._2)
    })
    new Sentence(mDoc).setRange(0, stringDoc.length - 1)
    val docs = sc.parallelize(Seq(mDoc))
  }

  "A WordEncoder" should "uniquely encode strings" in new TestDocument {
    val we = StringIndexer.createWordEncoder(docs)
    val nbrUniqWords = words.filter(Filters.wordFilter).toSet.size
    we.vocabSize() should equal (words.toSet.size)
    val indices = words.filter(Filters.wordFilter).map(we.index)
    indices.length should equal (words.filter(Filters.wordFilter).length)
    indices.toSet.size should equal (nbrUniqWords)
  }
}

