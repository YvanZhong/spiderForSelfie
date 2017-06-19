package utils

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer

import scala.collection.immutable
import scala.concurrent.Future

/**
  * Created by Zhong on 2017/5/10.
  */
trait HttpUtil {

  implicit val system: ActorSystem

  def get(name: String, url: String, parameters: List[(String, String)], headers: immutable.Seq[HttpHeader] = Nil)
         (implicit fm: Materializer): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(uri = url, headers = headers))
  }

}
