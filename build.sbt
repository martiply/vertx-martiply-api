import sbtassembly.PathList

name := "vertx-martiply-api"

version := "0.3.1"

scalaVersion := "2.12.7"
lazy val vertxVersion = "3.5.4"

mainClass in assembly := Some("com.martiply.api.Application")

resolvers += "jcenter" at "https://jcenter.bintray.com/"
resolvers += "jitpack" at "https://jitpack.io"

scalacOptions := Seq("-unchecked", "-deprecation")
scalacOptions += "-feature"


libraryDependencies ++= Seq(
  "org.scalatest"     %% "scalatest"            % "3.0.1"         % Test,
  "org.mockito"       % "mockito-core"          % "2.19.1"         % Test
)

libraryDependencies += "io.vertx" %% "vertx-lang-scala" % vertxVersion
libraryDependencies += "io.vertx" %% "vertx-config-scala" % vertxVersion
libraryDependencies += "com.github.jasync-sql" % "jasync-mysql" % "0.8.41"
libraryDependencies += "org.scala-lang.modules" % "scala-java8-compat_2.12" % "0.9.0"
libraryDependencies += "com.jsoniter" % "jsoniter" % "0.9.23"
libraryDependencies += "com.github.inmyth" % "scala-mylogging" % "26b5b2c"
libraryDependencies += "com.github.martiply" % "java-martiply-model" % "d881482"
libraryDependencies += "com.github.martiply" % "java-martiply-table" % "0cfc3e1"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.last
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
  case PathList("codegen.json") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

// libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"