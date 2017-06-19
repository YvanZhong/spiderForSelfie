package spiderForSelfie

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import spiderForSelfie.Actor.Spider

import scala.collection.mutable.ArrayBuffer
import scala.io.StdIn
/**
  * Created by Zhong on 2017/5/10.
  */
object Main {
  def main(args: Array[String]) {

    implicit val system = ActorSystem("spiderForSelfie-System", ConfigFactory.parseResources("product.conf").withFallback(ConfigFactory.load()))
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    System.setProperty("http.proxyHost", "localhost")
    System.setProperty("http.proxyPort", "8888")
    System.setProperty("https.proxyHost", "localhost")
    System.setProperty("https.proxyPort", "8888")

    val spider = system.actorOf(Spider.props(), "Spider")
    spider ! Spider.Start

//    val route =
//      path("hello") {
//        get {
//          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
//        }
//      }
//
//    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
//
//    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
//    StdIn.readLine() // let it run until user presses return
//    bindingFuture
//      .flatMap(_.unbind()) // trigger unbinding from the port
//      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
