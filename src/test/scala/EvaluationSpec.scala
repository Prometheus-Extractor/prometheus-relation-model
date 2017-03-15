import java.nio.file.{Files, Paths}

import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import se.lth.cs.docforia.Document
import se.lth.cs.docforia.memstore.MemoryDocument
import se.lth.cs.docforia.graph.text.{Sentence, Token}

import scala.collection.JavaConverters._
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SQLContext
import com.sony.prometheus.evaluation._
import com.holdenkarau.spark.testing.SharedSparkContext
import com.sony.prometheus._

class EvaluationSpec extends FlatSpec with BeforeAndAfter with Matchers with SharedSparkContext {
  "EvaluationDataReader" should "read json file properly" in {
    implicit val sqlContext = new SQLContext(sc)
    val edPointsRDD: RDD[EvaluationDataPoint] = EvaluationDataReader.load("./src/test/data/evaluationTest.txt")
    val edPoints = edPointsRDD.collect()
    edPoints.head.wd_sub should equal ("Q3388789")
    edPoints.head.wd_obj should equal ("Q60")
    edPoints.head.wd_pred should equal ("P19")
    edPoints.head.obj should equal ("/m/02_286")
    edPoints.head.sub should equal ("/m/026_tl9")
    val j0 = edPoints.head.judgments.head
    j0.judgment should equal ("yes")
    j0.rater should equal ("11595942516201422884")
    val j1 = edPoints.head.judgments(1)
    j1.judgment should equal ("yes")
    j1.rater should equal ("16169597761094238409")
    val e0 = edPoints.head.evidences.head
    e0.url should equal ("http://en.wikipedia.org/wiki/Morris_S._Miller")
  }

  "EvaluationDataReader" should "extract snippets into annotated docs" in {
    implicit val sqlContext = new SQLContext(sc)
    val docs: RDD[Document] = EvaluationDataReader.getAnnotatedDocs("./src/test/data/evaluationTest.txt")
    val expected = """Morris Smith Miller (July 31, 1779 -- November 16, 1824) was
    |a United States Representative from New York. Born in New York City, he
    |graduated from Union College in Schenectady in 1798. He studied law and was
    |admitted to the bar. Miller served as private secretary to Governor Jay, and
    |subsequently, in 1806, commenced the practice of his profession in Utica. He was
    |president of the village of Utica in 1808 and judge of the court of common
    |pleas of Oneida County from 1810 until his death.""".stripMargin.replaceAll("\n", " ")

    docs.collect().mkString should equal (expected)
  }

  "Evaluator" should "evaluate" in {
    implicit val sqlContext = new SQLContext(sc)
    val tempDataPath = "../data"
    val relationsPath = "../data/entities"

    // First check that the required files are present, otherwise the test will take a long time
    Files.exists(Paths.get(tempDataPath)) should be (true)
    Files.exists(Paths.get(tempDataPath + "/entities")) should be (true)
    Files.exists(Paths.get(relationsPath)) should be (true)

    // Run the pipeline
    val corpusData = new CorpusData("empty")(sc)
    val relationsData = new RelationsData(relationsPath)(sc)
    val trainingTask = new TrainingDataExtractorStage(
      tempDataPath + "/training_sentences",
      corpusData,
      relationsData)(sqlContext, sc)
    val featureTransformerTask = new FeatureTransformerStage(
      tempDataPath + "/feature_model",
      corpusData)(sqlContext, sc)
    val featureExtractionTask = new FeatureExtractorStage(
      tempDataPath + "/features",
      featureTransformerTask,
      trainingTask)(sqlContext, sc)
    val modelTrainingTask = new RelationModelStage(
      tempDataPath + "/model",
      featureExtractionTask,
      featureTransformerTask,
      relationsData)(sqlContext, sc)
    val predictor = Predictor(modelTrainingTask, featureTransformerTask, relationsData)

    val path = modelTrainingTask.getData()
    Files.exists(Paths.get(path)) should be (true)

    val evalDataPoints = EvaluationDataReader.load("./src/test/data/evaluationTest.txt")
    Evaluator.evaluate(evalDataPoints, predictor)(sqlContext, sc)
  }
}
