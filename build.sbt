
name := "raft"

version := "0.1"

scalaVersion := "2.13.6"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.12" % "3.0.5" % "test",
  "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
  "org.wvlet.airframe" %% "airframe-log" % "19.6.1",
  "com.typesafe.akka" %% "akka-actor" % "2.6.15",
)

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
)
libraryDependencies ++= Seq(
  "com.google.protobuf" % "protobuf-java" % "3.13.0" % "protobuf"
)

libraryDependencies += "io.grpc" % "protoc-gen-grpc-java" % "1.23.0" asProtocPlugin()
libraryDependencies += "joda-time" % "joda-time" % "2.10.10"


Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value)
