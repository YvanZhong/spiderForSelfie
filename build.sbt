name := "spiderForSelfie"

version := "0.1.4"

scalaVersion := "2.12.2"

val akkaV = "2.5.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.0.6",
  "ch.qos.logback" % "logback-classic" % "1.1.3" withSources(),
  "com.typesafe.akka" %% "akka-actor" % akkaV withSources(),
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.6"
)

// Automatically find def main(args:Array[String]) methods from classpath
packAutoSettings