package com.sony.prometheus.stages


import com.sony.prometheus.Prometheus
import com.sony.prometheus.utils.Pruner
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import play.api.libs.json._
import se.lth.cs.docforia.Document
import se.lth.cs.docforia.graph.text.Sentence

import scala.collection.JavaConverters._
import com.sony.prometheus.utils.Utils.pathExists
import org.apache.log4j.LogManager

/**
  * Performs extractions on `corpusData` corpus by collecting all predictions on each sentence in the corpus made by
  * the RelationModel `model`
  */
class PredictorStage(path: String, corpusData: CorpusData, model: RelationModel, posEncoder: PosEncoderStage,
                     word2VecData: Word2VecData, neTypeEncoder: NeTypeEncoderStage,
                     dependencyEncoderStage: DependencyEncoderStage, relationConfig: RelationConfigData)
                    (implicit sqlContext: SQLContext, sparkContext: SparkContext) extends Task with Data {

  override def getData(): String = {
    if (!pathExists(path)) {
      run()
    }
    path
  }

  override def run(): Unit = {
    LogManager.getLogger(classOf[PredictorStage]).info("Extracting relations from corpus")

    val predictor = Predictor.apply(model, posEncoder, word2VecData, neTypeEncoder, dependencyEncoderStage, relationConfig)
    val corpus = CorpusReader.readCorpus(corpusData.getData())
    val data = predictor.extractRelations(corpus)

    import sqlContext.implicits._
    data.cache()
    data.flatMap(d => d).toDF().write.json(path)
    data.unpersist(false)
  }
}

object Predictor {

  def apply(model: RelationModel,  posEncoder: PosEncoderStage, word2VecData: Word2VecData,
            neTypeEncoder: NeTypeEncoderStage, dependencyEncoderStage: DependencyEncoderStage, relationConfig: RelationConfigData)
           (implicit sqlContext: SQLContext): Predictor = {

    val featureTransformer = FeatureTransformer(word2VecData.getData(), posEncoder.getData(), neTypeEncoder.getData(),
                                                dependencyEncoderStage.getData())
    val relations = RelationConfigReader.load(relationConfig.getData())
    val ft = sqlContext.sparkContext.broadcast(featureTransformer)
    val pruner = Pruner(relationConfig.getData())
    new Predictor(model, ft, relations, pruner)
  }

  def load(path: String)(implicit sqlContext: SQLContext): RDD[ExtractedRelation] = {
    import sqlContext.implicits._
    sqlContext.read.json(path).as[ExtractedRelation].rdd
  }
}

class Predictor(model: RelationModel, transformer: Broadcast[FeatureTransformer], relations: Seq[Relation], pruner: Broadcast[Pruner]) extends Serializable {

  val UNKNOWN_CLASS = "<unknown_class>"
  val SENTENCE_MIN_LENGTH = 5
  val SENTENCE_MAX_LENGTH = 300
  val classIdxToId: Map[Int, String] = relations.map(r => (r.classIdx, r.id)).toList.toMap
  val threshold = Prometheus.conf.probabilityCutoff()


  def extractRelations(docs: RDD[Document])(implicit sqlContext: SQLContext): RDD[Seq[ExtractedRelation]] = {
    docs.map(extractDoc)
  }

  def extractRelationsLocally(docs: Seq[Document])(implicit sqlContext: SQLContext): Seq[Seq[ExtractedRelation]] = {
    docs.map(extractDoc)
  }

  private def extractDoc(doc: Document)(implicit sqlContext: SQLContext): Seq[ExtractedRelation] = {
    val sentences = doc.nodes(classOf[Sentence])
      .asScala
      .toSeq
      .map(s => doc.subDocument(s.getStart, s.getEnd))
      .filter(s => s.length >= SENTENCE_MIN_LENGTH && s.length <= SENTENCE_MAX_LENGTH)

    val points: Seq[TestDataPoint] = FeatureExtractor.testData(sentences)
    val classes = points
      .map(p => (p, transformer.value.toFeatureVector(p.wordFeatures, p.posFeatures, p.wordsBetween, p.posBetween, p.ent1PosFeatures, p.ent2PosFeatures,
        p.ent1Type, p.ent2Type, p.dependencyPath, p.ent1DepWindow, p.ent2DepWindow, p.ent1IsSubject)))
      .map(x => (model.predict(x._2, threshold), x._1))
      .filter(_._1.clsIdx != FeatureExtractor.NEGATIVE_CLASS_NBR)
      .filter(x => pruner.value.prunePrediction(x._1, x._2))

    classes.map{
      case (result: Prediction, point: TestDataPoint) =>
        val predicate = classIdxToId.getOrElse(result.clsIdx, s"$UNKNOWN_CLASS: $result")
        val subj = point.qidSource
        val obj = point.qidDest
        ExtractedRelation(subj, predicate, obj, point.sentence.text(), doc.uri(),
          result.probability, result.filterProb, result.classProb)
    }.toList
  }

}

case class ExtractedRelation(
  subject: String,
  predictedPredicate: String,
  obj: String,
  sentence: String,
  source: String,
  probability: Double,
  filterProbability: Double,
  classificationProbability: Double)

object ExtractedRelation {
  implicit val extractedRelationFormat = Json.format[ExtractedRelation]
}

