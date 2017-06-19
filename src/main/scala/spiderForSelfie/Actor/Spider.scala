package spiderForSelfie.Actor

import java.io.{File, FileWriter, PrintWriter}

import akka.actor.{Actor, Props}
import org.slf4j.LoggerFactory
import utils.HttpUtil
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import java.net.Proxy

import scala.util.{Failure, Success}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.Future
import scala.io.Source

/**
  * Created by Zhong on 2017/5/10.
  */
object Spider {
  def props(): Props = Props(new Spider())

  case object Start

  case object End

  case object GetHomePage

  case class Query(p: String, cookies: Seq[HttpCookie], file: File)

  case class AddNodes(nodes: List[Node], file: File)

  case object WriteToFile

  case class Media(count: Int, page_info: PageInfo, nodes: List[Node])

  case class PageInfo(start_cursor: String, end_cursor: String, has_next_page: Boolean, has_previous_page: Boolean)

  case class Node(thumbnail_src: String, comments: Comments, likes: Likes, id: String, code: String, date: Long)

  case class Comments(count: Int)

  case class Likes(count: Int)

  case class QueryRsp(media: Media, status: String)

//  case class QueryDetail(code: String)
  case object QueryDetail

  case class Owner(username: String)
  case class Shortcode_media(owner: Owner)
  case class Graphql(shortcode_media: Shortcode_media)
  case class QueryDetailRsp(graphql: Graphql)

  case class QueryUser(name: String, node: Node, file: File)

  case class Data(node: Node, followers: Int, file: File)

  case class AddData(data: Data)

  implicit val likesFormat = jsonFormat1(Likes)
  implicit val commentsFormat = jsonFormat1(Comments)
  implicit val nodeFormat = jsonFormat6(Node)
  implicit val pageInfoFormat = jsonFormat4(PageInfo)
  implicit val mediaFormat = jsonFormat3(Media)
  implicit val queryRspFormat = jsonFormat2(QueryRsp)

  implicit val ownerFormat = jsonFormat1(Owner)
  implicit val shortcode_mediaFormat = jsonFormat1(Shortcode_media)
  implicit val graphqlF = jsonFormat1(Graphql)
  implicit val queryDetailRspF = jsonFormat1(QueryDetailRsp)

}

class Spider extends Actor {

  import Spider._

  private val log = LoggerFactory.getLogger(this.getClass)

  implicit private val system = context.system

  implicit val dispatcher = context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val selfRef = context.self
  private val logPrefix = selfRef.path

  private var nodeList = List[(List[Node], File)]()

  private var dataList = List[Data]()

  import scala.concurrent.duration._
  context.system.scheduler.scheduleOnce(10.seconds, selfRef, WriteToFile)

  private val http = Http()

  override def receive: Receive = idle()

  def idle(): Receive = {
    case Start =>
      log.debug(s"$logPrefix I got Start")
      context.become(work())
      selfRef ! GetHomePage


    case other =>
      log.debug(s"$logPrefix I got unknown msg: $other")
  }

  def work(): Receive = {
    case GetHomePage =>
      log.debug(s"$logPrefix I got GetHomePage")
      val file = new File(s"./${System.currentTimeMillis()}.txt")
      if (!file.exists()) file.createNewFile()
      http.singleRequest(HttpRequest(uri = "https://www.instagram.com/explore/tags/selfie/")).onComplete {
        case Success(rsp) =>
          try {
            rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
              try {
                val bodyStr = body.utf8String
                //            log.info("Got response, body: " + bodyStr)
                import akka.http.scaladsl.model.headers._
                val cookies = rsp.headers.collect {
                  case c: `Set-Cookie` => c.cookie
                }

                //println("headers:")
                //rsp.headers.foreach(println)

                //println("cookies:")
                //            cookies.foreach{
                //              println
                //            }
                val pattern =
                """"end_cursor.*"""".r
                val findRst = pattern.findFirstIn(bodyStr)
                //            log.debug(s"findRst: $findRst")
                findRst.foreach {
                  s =>
                    log.debug(s"${s.split('}').head.replace("\"", "").split(':').last}")
                    selfRef ! Query(s.split('}').head.replace("\"", "").split(':').last.trim, cookies, file)
                }
              } catch {
                case e: Exception =>
                  log.debug(s"$logPrefix GetHomePage parse error: e")
                  context.system.scheduler.scheduleOnce(30.seconds, selfRef, GetHomePage)

              }
            }

          } catch {
            case e: Exception =>
              log.debug(s"GetHomePage rsp.entity.dataBytes error: $e")
              context.system.scheduler.scheduleOnce(30.seconds, selfRef, GetHomePage)
          }

        case Failure(e) =>
          e.printStackTrace()
          log.debug(s"$logPrefix GetHomePage failed error: e")
          context.system.scheduler.scheduleOnce(30.seconds, selfRef, GetHomePage)
      }
    //      HttpUtil.get("getHomePage", "https://www.instagram.com/explore/tags/selfie/", Nil,)

    case msg: Query =>
      log.debug(s"$logPrefix I got msg: $msg")
      //HttpCookiePair("ig_pr", "0.8999999761581421"),      HttpCookiePair("ig_vw", "2133"),
    val c = Cookie(msg.cookies.map(c => c.pair()).toList ::: List(HttpCookiePair("csrftoken", "xFUsQlOWCKI4CADC91IzCcCdkXPJeMVD")))
      val headers = `User-Agent`("Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.133 Safari/537.36") :: c :: List(
       // RawHeader("Content-Type", "application/x-www-form-urlencoded"),
        RawHeader("Origin", "www.instagram.com"),
        RawHeader("Referer", "https://www.instagram.com/explore/tags/selfie/"),
        RawHeader("X-CSRFToken", "xFUsQlOWCKI4CADC91IzCcCdkXPJeMVD"),
        RawHeader("X-Instagram-AJAX", "1")
        //RawHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.133 Safari/537.36")
      )

      def entity(q: String) = HttpEntity.Strict(
        ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`), {
          val s =
            s"""ig_hashtag(selfie) { media.after($q, 100) {
  count,
  nodes {
    __typename,
    caption,
    code,
    comments {
      count
    },
    comments_disabled,
    date,
    dimensions {
      height,
      width
    },
    display_src,
    id,
    is_video,
    likes {
      count
    },
    owner {
      id
    },
    thumbnail_src,
    video_views
  },
  page_info
}
 }""" //s"q=ig_hashtag(selfie) { media.after($q, 8) { count, nodes { __typename, caption, code, comments { count }, comments_disabled, date, dimensions { height, width }, display_src, id, is_video, likes { count }, owner { id }, thumbnail_src, video_views }, page_info } }" +
          ByteString("q=" + java.net.URLEncoder.encode(s, "utf8") + "&ref=tags%3A%3Ashow&query_id=")
        }
      )

      //https://www.instagram.com/query/
      val uri = "https://www.instagram.com/query/" // "http://localhost:8888"
      //val uri = "http://localhost:8888"
      http.singleRequest(HttpRequest(method = HttpMethods.POST, uri = uri, entity = entity(msg.p)).withHeaders(headers)).onComplete {
        case Success(rsp) =>
          try {
            rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
              //log.info("Got response, body: " + bodyStr)
              try{
                val bodyStr = body.utf8String
                val json = bodyStr.parseJson
                val queryRsp = jsonReader(queryRspFormat).read(json)
                //log.debug(s"queryRsp: $queryRsp")
                selfRef ! Query(queryRsp.media.page_info.end_cursor, msg.cookies, msg.file)
                val time = System.currentTimeMillis() / 1000
                selfRef ! AddNodes(queryRsp.media.nodes.map{
                  n =>
                    n.copy(date = time - n.date)
                }, msg.file)

              } catch {
                case e: Exception =>
                  log.debug(s"Query json parse exception: $e")
                  selfRef ! GetHomePage
              }

              //            val pattern = """^"end_cursor.*"$""".r
              //            pattern.findFirstIn(bodyStr).foreach{
              //              s =>
              //                selfRef ! Query(s.split(":").last)
              //            }

            }
          } catch {
            case e: Exception =>
              log.debug(s"Query rsp.entity.dataBytes exception: $e")
              context.system.scheduler.scheduleOnce(30.seconds, selfRef, GetHomePage)
          }

        case Failure(e) =>
          log.debug(s"Query failure: $e")
          e.printStackTrace()
          selfRef ! GetHomePage
      }

    case msg: AddNodes =>
      log.debug(s"$logPrefix I got AddNodes: ${msg.nodes.length}")
      nodeList +:= (msg.nodes, msg.file)
      selfRef ! QueryDetail

    case QueryDetail =>
      //log.debug(s"$logPrefix I got QueryDetail")
      if (nodeList.nonEmpty) {
        val head = nodeList.head
        nodeList = nodeList.tail
        head._1.foreach{
          n =>
            Http().singleRequest(HttpRequest(uri = s"https://www.instagram.com/p/${n.code}/?tagged=selfie&__a=1")).onComplete{
              case Success(rsp) =>
                rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                  val bodyStr = body.utf8String
                  //log.info("Got response, body: " + bodyStr)
                  try {
                    val json = bodyStr.parseJson
                    val queryRsp = jsonReader(queryDetailRspF).read(json)
                    //log.debug(s"queryRsp: $queryRsp")
                    selfRef ! QueryUser(name = queryRsp.graphql.shortcode_media.owner.username, n, head._2)

                  } catch {
                    case e: Exception =>
                      //                  e.printStackTrace()
                      log.debug(s"QueryDetail json parse error code: ${n.code}, bodyStr: $bodyStr, \n error: $e")
                  }
                }

              case Failure(e) =>
                log.debug(s"QueryDetail failure: $e")
            }
        }
        selfRef ! QueryDetail
      }

    case msg: QueryUser =>
//      log.debug(s"$logPrefix I got QueryUser: $msg")
      Http().singleRequest(HttpRequest(uri = s"https://www.instagram.com/${msg.name}/?hl=en")).onComplete {
        case Success(rsp) =>
          rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
            val bodyStr = body.utf8String

            val pattern = """"count": .*, "followed_by_viewer"""".r
            val findRst = pattern.findFirstIn(bodyStr)
            //            log.debug(s"findRst: $findRst")
            findRst.foreach {
              s =>
                val followers = s.span(_ != '}')._1.drop(9).dropRight(0).toInt
                selfRef ! AddData(Data(msg.node, followers, msg.file))
            }
          }

        case Failure(e) =>
          e.printStackTrace()
      }

    case msg: AddData =>
      dataList +:= msg.data
      log.debug(s"datalength: ${dataList.length}")

    case WriteToFile =>
      log.debug(s"$logPrefix start to write to file===========")
      val tmp = dataList
      dataList = Nil
      Future {
        tmp.groupBy(_.file).foreach{
          e =>
            val fw = new FileWriter(e._1, true)
            val writer = new PrintWriter(fw)
            e._2.foreach{
              d =>
                writer.println(d.node.thumbnail_src+"\t"+d.node.comments.count+"\t"+d.node.likes.count+"\t"+d.node.date+"\t"+d.followers)
            }
            writer.close()
        }
        log.debug(s"$logPrefix finish write to file=============")
      }
      import scala.concurrent.duration._
      context.system.scheduler.scheduleOnce(60.seconds, selfRef, WriteToFile)

    case End =>
      log.debug(s"$logPrefix I got End")
      context.stop(selfRef)

    case other =>
      log.debug(s"$logPrefix I got unknown msg: $other")
  }

  def busy(): Receive = {
    ???

  }

}
