package com.martiply.api

import java.util.TimeZone

import com.jsoniter.output.JsonStream
import com.martiply.api.model.MtpResponse.ErrorEnum
import com.martiply.api.model.MtpResponse.ErrorEnum.ErrorEnum
import com.martiply.api.model.{Item, MtpResponse}
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.config.ConfigRetriever
import io.vertx.scala.core.http.HttpServerRequest
import io.vertx.scala.core.{DeploymentOptions, Vertx}
import me.mbcu.scala.{MyLogging, MyLoggingSingle}

import scala.io.Source
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
      val rep = Repository(fcnf.getJsonObject("database"))
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
            val kwd = req.getParam("keyword")
            val lat = req.getParam("lat").flatMap(v=> Try(v.toDouble).toOption).filter(v => v >= -90 && v <= 90)
            val lng = req.getParam("lng").flatMap(v=> Try(v.toDouble).toOption).filter(v => v >= -180 && v <= 180)
            val cat = req.getParam("category").flatMap(Item.findCategory)
            val rid = req.getParam("userid")

            (kwd, lat, lng) match {
              case (Some(kw), Some(lt), Some(ln)) => rep.get.search(kw, lt, ln, legitSaleTs, cat) map (z => jSuccess(req, z)) recover { case e => logPipe(req, e)}
              case _ => jError(req, ErrorEnum.invalidParam)
            }

          case p if p contains cPaths.getString("SEARCH_STORE_KEYWORD") =>
            val kwd = req.getParam("keyword")
            val sid = req.getParam("storeid").flatMap(v=> Try(v.toInt).toOption)
            val rid = req.getParam("userid")

            (kwd, sid) match {
              case (Some(kw), Some(sd)) => rep.get.searchStoreKeyword(kw, sd, legitSaleTs) map (z => jSuccess(req, z)) recover { case e => logPipe(req, e)}
              case _ => jError(req, ErrorEnum.invalidParam)
            }

          case p if p contains cPaths.getString("SEARCH_STORE_RANDOM") =>
            val sid = req.getParam("storeid").flatMap(v=> Try(v.toInt).toOption)
            val rid = req.getParam("userid")

            sid match {
              case Some(sd) => rep.get.searchStoreRandom(sd, legitSaleTs) map (z => jSuccess(req, z)) recover { case e => logPipe(req, e)}
              case _ => jError(req, ErrorEnum.invalidParam)
            }

          case p if p contains cPaths.getString("GET_AREA_STORES") =>
            val lat = req.getParam("lat").flatMap(v=> Try(v.toDouble).toOption).filter(v => v >= -90 && v <= 90)
            val lng = req.getParam("lng").flatMap(v=> Try(v.toDouble).toOption).filter(v => v >= -180 && v <= 180)
            val rid = req.getParam("userid")

            (lat, lng) match {
              case (Some(lt), Some(ln)) =>  rep.get.getStores(lt, ln) map (z => jSuccess(req, z)) recover { case e => logPipe(req, e)}
              case _ => jError(req, ErrorEnum.invalidParam)
            }

          case p if p contains cPaths.getString("GET_STORE") =>
            val sid = req.getParam("storeid").flatMap(v=> Try(v.toInt).toOption)
            val rid = req.getParam("userid")

            sid match {
              case Some(sd) => rep.get.getStore(sd) map (z => jSuccess(req, z)) recover { case e => logPipe(req, e)}
              case _ => jError(req, ErrorEnum.invalidParam)
            }

          case _ => jError(req, ErrorEnum.unknownApi)

        }
      })
        .listenFuture(port).onComplete {
        case Success(result) => info(s"Server is now listening at $port")

        case Failure(cause) =>
          error(cause.getMessage)
          System.exit(0)
      }

    }

    def logPipe(req: HttpServerRequest, e: Throwable) : Unit = {
      error(e.getMessage)
      jError(req, ErrorEnum.dbError)
    }

    def jError(req: HttpServerRequest, errorEnum: ErrorEnum): Unit = req.response.end(JsonStream.serialize(MtpResponse.error(errorEnum)))

    def jSuccess[A](req: HttpServerRequest, load: MtpResponse[A]): Unit =  req.response.end(JsonStream.serialize(load))
  }

}


