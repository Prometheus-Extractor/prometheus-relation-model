name := """prometheus-relation-model"""

version := "0.0.1-SNAPSHOT"

organization := "sonymobile"

scalaVersion := "2.10.6"
ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
dependencyOverrides += "org.scala-lang" % "scala-compiler" % scalaVersion.value
publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))

resolvers += Resolver.mavenLocal
resolvers += "Akka repository" at "http://repo.akka.io/releases"
resolvers += "cloudera" at "https://repository.cloudera.com/artifactory/cloudera-repos/"
resolvers += "JBoss" at "https://repository.jboss.org/"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.6"
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.6"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
libraryDependencies += "org.rogach" %% "scallop" % "2.1.0"

lazy val spark_core_version = "1.6.0-cdh5.10.0"
lazy val spark_sql_version = "1.6.0-cdh5.10.0"
lazy val spark_mlib_version = "1.6.0-cdh5.10.0"

lazy val sparks = Seq(
  "org.xerial.snappy" % "snappy-java" %"1.1.2.1",
  "org.apache.spark" % "spark-core_2.10" % spark_core_version,
  "org.apache.spark" % "spark-mllib_2.10" % spark_mlib_version,
  "org.apache.spark" % "spark-sql_2.10" % spark_sql_version
)

if (sys.props.getOrElse("mode", default = "local") == "cluster") {
  println("Running for cluster!")
  libraryDependencies ++= sparks.map(_ % "provided")
} else {
  libraryDependencies ++= sparks
}

libraryDependencies += "se.lth.cs.nlp" % "docforia" % "1.0-SNAPSHOT"

// ScalaDoc site generation
enablePlugins(SiteScaladocPlugin)
enablePlugins(GhpagesPlugin)
git.remoteRepo := "git@github.com:ErikGartner/prometheus-relation-model.git"
