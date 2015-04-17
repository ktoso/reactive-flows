import ByteConversions._

lazy val reactiveFlows = project
  .in(file("."))
  .configs(MultiJvm)
  .enablePlugins(AutomateHeaderPlugin, GitVersioning, ConductRPlugin)

name := "reactive-flows"

libraryDependencies ++= List(
  Library.akkaActor,
  Library.akkaCluster,
  Library.akkaContrib,
  Library.akkaDataReplication,
  Library.akkaHttp,
  Library.akkaHttpJsonSpray,
  Library.akkaSlf4j,
  Library.akkaSse,
  Library.conductrBundleLib,
  Library.logbackClassic,
  Library.akkaHttpTestkit      % "test",
  Library.akkaMultiNodeTestkit % "test",
  Library.akkaTestkit          % "test",
  Library.scalaTest            % "test"
)

initialCommands := """|import de.heikoseeberger.reactiveflows._""".stripMargin

addCommandAlias("rf1", "reStart -Dakka.remote.netty.tcp.port=2551 -Dreactive-flows.http-service.port=9001 -Dakka.cluster.roles.0=shared-journal")
addCommandAlias("rf2", "run     -Dakka.remote.netty.tcp.port=2552 -Dreactive-flows.http-service.port=9002")
addCommandAlias("rf3", "run     -Dakka.remote.netty.tcp.port=2553 -Dreactive-flows.http-service.port=9003")

BundleKeys.nrOfCpus  := 1.0
BundleKeys.memory    := 256.MiB
BundleKeys.diskSpace := 256.MB
BundleKeys.system    := "reactive-flows-system"
BundleKeys.endpoints := Map(
  "akka-remote" -> Endpoint("tcp", 0, Set.empty),
  "reactive-flows" -> Endpoint("http", 0, Set(URI("http://:8080/reactive-flows"), URI("http://:9000")))
)
