import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.cloud.tools.jib.api.ImageReference
import com.typesafe.config.ConfigFactory
import play.api.libs.json.Format
import skuber.LabelSelector.dsl._
import skuber.api.client.KubernetesClient
import skuber.apps.v1.{Deployment, DeploymentList}
import skuber.ext.Ingress
import skuber.json.ext.format._
import skuber.json.format._
import skuber.{Container, HTTPGetAction, LabelSelector, ObjectMeta, ObjectResource, Pod, Probe, ResourceDefinition, Service, k8sInit}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success} // reuse some core formatters

trait Applyable[O <: ObjectResource] {
  def apply(oldVersion: O, newVersion: O): O
}

object Applyable {

  implicit object ApplyableDeployment extends Applyable[Deployment] {
    def apply(oldVersion: Deployment, newVersion: Deployment): Deployment = {
      newVersion.copy(
        metadata = newVersion.metadata.copy(
          resourceVersion = oldVersion.metadata.resourceVersion))
    }
  }

  implicit object ApplyableService extends Applyable[Service] {
    def apply(oldVersion: Service, newVersion: Service): Service = {
      newVersion.copy(
        metadata = newVersion.metadata.copy(
          resourceVersion = oldVersion.metadata.resourceVersion),
        spec = Some(newVersion.copySpec.copy(
          clusterIP = oldVersion.copySpec.clusterIP,
        )),
      )
    }
  }

  implicit object ApplyableIngress extends Applyable[Ingress] {
    def apply(oldVersion: Ingress, newVersion: Ingress): Ingress = {
      newVersion.copy(
        metadata = newVersion.metadata.copy(
          resourceVersion = oldVersion.metadata.resourceVersion))
    }

  }

  implicit class ApplyUtil[A <: ObjectResource](oldVersion: A) {
    def apply(newVersion: A)(implicit makesApplyable: Applyable[A]): A = {
      makesApplyable.apply(oldVersion, newVersion)
    }
  }

}

object Deployer {

  def deploy(name: String, ingressHost: String, imageRef: ImageReference, referenceConfig: File): Unit = {
    val config = ConfigFactory.parseFile(referenceConfig).resolve()
    implicit val system = ActorSystem("deployer", config, this.getClass.getClassLoader)
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher
    implicit val k8s = k8sInit(config)

    val labelSelector = LabelSelector("app.kubernetes.io/name" is name.toString)
    val labels = Map("app.kubernetes.io/name" -> name.toString)
    val objectMeta = ObjectMeta(name.toString, labels = labels)
    val deployment: Deployment = Deployment(
      metadata = objectMeta,
      spec = Some(Deployment.Spec(
        selector = labelSelector,
        template = Pod.Template.Spec(
          metadata = ObjectMeta(labels = labels),
          spec = Some(Pod.Spec(
            containers = List(Container(
              name = name.toString,
              image = imageRef.toString,
              ports = List(Container.Port(containerPort = 8080, name = "http")),
              readinessProbe = Some(Probe(HTTPGetAction(Right("http"))))
            )),
          ))
        )
      ))
    )
    val service: Service = Service(
      metadata = objectMeta,
      spec = Some(Service.Spec(
        ports = List(Service.Port("http", skuber.Protocol.TCP, 80, Some(Right("http")))),
        selector = labels,
      )),
    )
    val ingress: Ingress = Ingress(
      metadata = objectMeta.copy(annotations = Map("easyssl.ecg.so/managed" -> "true")),
      spec = Some(Ingress.Spec(
        rules = List(Ingress.Rule(
          Some(ingressHost),
          Ingress.HttpRule(List(Ingress.Path("/", Ingress.Backend(name, 80))))))
      ))
    )
    val eventualCreation = for {
      i <- createOrUpdate(name, ingress)
      d <- createOrUpdate(name, deployment)
      s <- createOrUpdate(name, service)
    } yield (d, s, i)

    eventualCreation.onComplete {
      case Success(_) => println("🚀 Deployed!")
      case Failure(e) => println("😿 Something went wrong: " + e)
    }
    Await.result(eventualCreation, 10.seconds)
  }

  def createOrUpdate[O <: ObjectResource](name: String, newObj: O)
                                         (implicit k8s: KubernetesClient, fmt: Format[O], rd: ResourceDefinition[O],
                                          ec: ExecutionContext, applyable: Applyable[O]): Future[O] = {
    import Applyable._
    k8s.getOption[O](name)
      .flatMap {
        case Some(oldObj) => k8s.update[O](oldObj.apply(newObj))
        case None => k8s.create[O](newObj)
      }
  }

  def list(name: String, referenceConfig: File): Unit = {
    val config = ConfigFactory.parseFile(referenceConfig).resolve()
    implicit val system = ActorSystem("deployer", config, this.getClass.getClassLoader)
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher
    val k8s = k8sInit(config)
    val eventualDeploymentList = k8s.list[DeploymentList]
    eventualDeploymentList.onComplete {
      case Success(deployments) => println("got deployments! " + deployments.items.map(d => d.metadata.name).toString())
      case Failure(_) => println("poop.")
    }
    Await.result(eventualDeploymentList, 10.seconds)
  }
}
