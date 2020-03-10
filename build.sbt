import complete.DefaultParsers._

lazy val akkaHttpVersion = "10.1.11"
lazy val akkaVersion = "2.6.3"
jibRegistry := "dock.es.ecg.tools"
jibOrganization := "pwildsmith"
jibName := "my-akka-http-project"
jibTargetImageCredentialHelper := {
  System.getProperty("os.name").toLowerCase match {
    case mac if mac.contains("mac") => Some("docker-credential-osxkeychain")
    case _ => Some("docker-credential-desktop")
  }
}
jibBaseImage := "dock.es.ecg.tools/hub.docker.com/adoptopenjdk.openjdk11:alpine-jre"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.13.1",
    )),
    name := "My Akka HTTP Project",
    version ~= (_.replace('+', '-')),
    dynver ~= (_.replace('+', '-')),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.lonelyplanet" %% "prometheus-akka-http" % "0.5.0",

      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    ),
    resolvers ++= Seq(
      Resolver.bintrayRepo("lonelyplanet", "maven")
    )
  )
  .enablePlugins(JibPlugin)

lazy val showVersion = taskKey[Unit]("Show version")
showVersion := {
  println(version.value)
}

lazy val deploy = inputKey[Unit]("Deploy to current Kubernetes context")
deploy := {
  val parsed: Seq[String] = spaceDelimited("<arg>").parsed
  parsed match {
    case Seq(ingressHost) =>
      val imageRef = jibImageBuild.value
      Deployer.deploy(jibName.value, ingressHost, imageRef, file("./project/config/reference.conf"))
    case _ => streams.value.log.error("An ingress hostname should be provided as an argument to 'deploy'")
  }
}

lazy val listDeployments = taskKey[Unit]("list deployments")
listDeployments := {
  Deployer.list("my-akka-http-project", file("./project/config/reference.conf"))
}
