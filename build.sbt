import skuber.Container

lazy val akkaHttpVersion = "10.1.11"
lazy val akkaVersion = "2.6.3"
jibRegistry := "dock.es.ecg.tools"
jibOrganization := "pe"
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

lazy val deploy = taskKey[Unit]("Deploy to current Kubernetes context")
deploy := {
  import scala.util.{Success, Failure}
  import akka.actor.ActorSystem
  import akka.stream.ActorMaterializer
  import skuber.{LabelSelector, ObjectMeta}
  import skuber.LabelSelector.dsl._
  import skuber.apps.v1.Deployment
  import skuber.Pod
  import skuber.k8sInit

  val imageRef = jibDockerBuild.value
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  val k8s = k8sInit

  val deployment = Deployment(
    metadata = ObjectMeta(name.toString(), labels = Map("app.kubernetes.io/name" -> name.toString())),
    spec = Some(Deployment.Spec(
      selector = LabelSelector("app.kubernetes.io/name" is name.toString()),
      template = Pod.Template.Spec(
        metadata = ObjectMeta(labels = Map("app.kubernetes.io/name" -> name.toString())),
        spec = Some(Pod.Spec(
          containers = List(Container(
            name = name.toString(),
            image = imageRef.toString(),
            ports = List(Container.Port(containerPort = 8080, name = "http")),
          ))
        ))
      )
    ))
  )
  k8s.create[Deployment](deployment).onComplete {
    case Success(_) => streams.value.log.success("Created deployment!")
    case Failure(e) => streams.value.log.error("sad face" + e)
  }
}
