package com.martiply.api

import java.sql.SQLException
import java.util.TimeZone

import com.jsoniter.output.JsonStream
import com.martiply.api.model.MtpResponse
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.config.ConfigRetriever
import io.vertx.scala.core.http.HttpServerRequest
import io.vertx.scala.core.{DeploymentOptions, Vertx}
import me.mbcu.scala.{MyLogging, MyLoggingSingle}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Application extends App {
  import scala.concurrent.ExecutionContext.Implicits.global
  if (args.length != 1){
    println("Log path needed")
    System.exit(0)
  }

  val vertx = Vertx.vertx()
  var rep: Option[Repository] = None
  val retriever = ConfigRetriever.create(vertx)
  val f1 = for {
    fcnf <- retriever.getConfigFuture()
    frep <- {
      val imgHost = fcnf.getString("imgHost")
      val rep = Repository(fcnf.getJsonObject("database"), imgHost)
      rep.testConnection()
    }
  } yield (fcnf, frep)

  f1 onComplete {

    case Success(value) =>
      val conf = value._1 // side effect, but vertx can't pass complex objects with eventbus
      rep = Some(value._2)
      MyLoggingSingle.init(args(0), TimeZone.getTimeZone(conf.getString("timezone")))
      vertx.deployVerticle(new ServerVerticle, DeploymentOptions().setConfig(conf))

    case Failure(e) =>
      println("If you see Config file path: path to config.json, format:json then the problem is Database access or Servlet server : " + e.getMessage)
      System.exit(0)
  }


  class ServerVerticle extends ScalaVerticle with MyLogging {

    override def start(): Unit = {
      val cfg  = vertx.getOrCreateContext().config().get
      val cPaths = cfg.getJsonObject("paths")
      val port = cfg.getInteger("port")

      vertx.createHttpServer().requestHandler(req =>{ // different handler each request
        val legitSaleTs : Int = (System.currentTimeMillis() / 1000).asInstanceOf[Int]
        req.response().putHeader("content-type", "application/json")
        req.path() match {

          case p if p.contains("/") =>   req.response().end("{\"message\":\"Martiply API\"}")

          case p if p contains cPaths.getString("SEARCH_AREA_KEYWORD") =>
            val f = for {
              fini <- Future {
                val kwd = req.getParam("keyword")
                val lat = req.getParam("lat").flatMap(v=> Try(v.toDouble).toOption).filter(v => v >= -90 && v <= 90)
                val lng = req.getParam("lng").flatMap(v=> Try(v.toDouble).toOption).filter(v => v >= -180 && v <= 180)
                val cat = req.getParam("category")
                val did = req.getParam("did")
                val apk = req.getParam("apikey")
                (kwd, lat, lng, cat, did, apk)
              }
              fque <- rep.get.search(fini._1.get, fini._2.get, fini._3.get, legitSaleTs, fini._4)
            } yield fque
            f map(p => jSuccess(req, p)) recover { case e => jError(req, e) }

          case p if p contains cPaths.getString("SEARCH_STORE_KEYWORD") =>
            val f = for {
              fini <- Future {
                val kwd = req.getParam("keyword")
                val sid = req.getParam("storeid").flatMap(v=> Try(v.toInt).toOption)
                val did = req.getParam("did")
                val apk = req.getParam("apikey")
                (kwd, sid, did, apk)
              }
              fque <- rep.get.searchStoreKeyword(fini._1.get, fini._2.get, legitSaleTs)
            } yield fque
            f map(p => jSuccess(req, p)) recover { case e => jError(req, e) }

          case p if p contains cPaths.getString("SEARCH_STORE_RANDOM") =>
            val f = for {
              fini <- Future {
                val sid = req.getParam("storeid").flatMap(v=> Try(v.toInt).toOption)
                val did = req.getParam("did")
                val apk = req.getParam("apikey")
                (sid, did, apk)
              }
              fque <-  rep.get.searchStoreRandom(fini._1.get, legitSaleTs)
            } yield fque
            f map(p => jSuccess(req, p)) recover { case e => jError(req, e) }

          case p if p contains cPaths.getString("GET_AREA_STORES") =>
            val f = for {
              fini <- Future {
                val lat = req.getParam("lat").flatMap(v=> Try(v.toDouble).toOption).filter(v => v >= -90 && v <= 90)
                val lng = req.getParam("lng").flatMap(v=> Try(v.toDouble).toOption).filter(v => v >= -180 && v <= 180)
                val did = req.getParam("did")
                val apk = req.getParam("apikey")
                (lat, lng, did, apk)
              }
              fque <- rep.get.getStores(fini._1.get, fini._2.get)
            } yield fque
            f map(p => jSuccess(req, p)) recover { case e => jError(req, e) }

          case p if p contains cPaths.getString("GET_STORE") =>
            val f = for {
              fini <- Future {
                val sid = req.getParam("storeid").flatMap(v=> Try(v.toInt).toOption)
                val did = req.getParam("did")
                val apk = req.getParam("apikey")
                (sid, did, apk)
              }
              fque <- rep.get.getStore(fini._1.get)
            } yield fque
            f map(p => jSuccess(req, p)) recover { case e => jError(req, e) }

          case _ => jError(req, NoApiException())

        }
      })
        .listenFuture(port).onComplete {
        case Success(result) => info(s"Server is now listening at $port")

        case Failure(cause) =>
          error(cause.getMessage)
          System.exit(0)
      }

    }

    case class NoApiException() extends Exception("Unknown path or parameter")

    def handleException(e: Throwable): String = {
      e match {
        case t: NoSuchElementException => "Missing parameter or incorrect parameter"
        case t: SQLException => "Database error"
        case _ => "Unknown error"
      }
    }

    def jError(req: HttpServerRequest, throwable: Throwable): Unit = req.response.end(JsonStream.serialize(MtpResponse.error(handleException(throwable))))

    def jSuccess[A](req: HttpServerRequest, load: MtpResponse[A]): Unit =  req.response.end(JsonStream.serialize(load))
  }

}


