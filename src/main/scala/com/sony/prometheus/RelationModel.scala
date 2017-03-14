package com.sony.prometheus

import org.apache.log4j.LogManager
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, LogisticRegressionWithLBFGS}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SQLContext}
import pipeline._


/** Builds the RelationModel
 */
class RelationModelStage(path: String, featureExtractor: Data, featureTransformerStage: Data,
                         relationsReader: Data)
                        (implicit sqlContext: SQLContext, sc: SparkContext) extends Task with Data {

  override def getData(): String = {
    if (!exists(path)) {
      run()
    }
    path
  }

  override def run(): Unit = {

    val data:RDD[TrainingDataPoint] = FeatureExtractor.load(featureExtractor.getData())
    val featureTransformer = FeatureTransformer.load(featureTransformerStage.getData())
    val numClasses = RelationsReader.readRelations(relationsReader.getData()).count().toInt + 1

    val model = RelationModel(data, featureTransformer, numClasses)
    model.save(path, data.sparkContext)
  }
}

/** Provides the RelationModel classifier
 */
object RelationModel {

  def printDataInfo(data: RDD[TrainingDataPoint], vocabSize: Int, numClasses: Int): Unit = {
    val log = LogManager.getLogger(RelationModel.getClass)
    log.info("Training Model")
    log.info(s"Vocab size: $vocabSize")
    log.info(s"Number of classes: $numClasses")
    log.info("Data distribution:")
    data.map(t => (t.relationId, 1)).reduceByKey(_+_).map(t=> s"${t._2}\t${t._1}").collect().map(log.info)
  }

  def apply(data: RDD[TrainingDataPoint], featureTransformer: FeatureTransformer, numClasses: Int)(implicit sqlContext: SQLContext): RelationModel = {

    var labeledData = data.map(t => {
      LabeledPoint(t.relationClass.toDouble, t.toFeatureVector(featureTransformer))
    })
    labeledData.cache()

    val classifier = new LogisticRegressionWithLBFGS()
    classifier.setNumClasses(numClasses)
    val model = classifier.run(labeledData)

    new RelationModel(model)
  }

  def load(path: String, context: SparkContext): RelationModel = {
    new RelationModel(LogisticRegressionModel.load(context, path))
  }

}

class RelationModel(model: LogisticRegressionModel) extends Serializable {

  def save(path: String, context: SparkContext): Unit = {
    model.save(context, path)
  }

  def predict(vector: Vector): Double = {
    model.predict(vector)
  }

  def predict(vectors: RDD[Vector]): RDD[Double] = {
    model.predict(vectors)
  }

}
