package com.martiply.api

import java.util.concurrent.TimeUnit

import com.github.jasync.sql.db.general.ArrayRowData
import com.github.jasync.sql.db.mysql.MySQLConnection
import com.github.jasync.sql.db.mysql.pool.MySQLConnectionFactory
import com.github.jasync.sql.db.pool.{ConnectionPool, PoolConfiguration}
import com.github.jasync.sql.db.{Configuration, Connection, SSLConfiguration}
import com.martiply.api.model._
import com.martiply.model.interfaces.AbsImg.Root
import com.martiply.model.interfaces.IItem.Condition
import com.martiply.table._
import io.vertx.core.json.JsonObject

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode

object Repository {
  def apply(mySqlConf: JsonObject, imgHost: String): Repository = {
    val con = buildClient(mySqlConf)
    new Repository(con, mySqlConf.getJsonObject("query"), imgHost)
  }

  def buildClient(mySqlConf: JsonObject): ConnectionPool[MySQLConnection] = {
    val pool = mySqlConf.getJsonObject("pool")
    val poolConfiguration = new PoolConfiguration(
      pool.getInteger("maxObjects"),
      TimeUnit.MINUTES.toMillis(pool.getLong("maxIdleMin")),
      pool.getInteger("maxQueueSize"),
      TimeUnit.SECONDS.toMillis(pool.getLong("validationIntervalSec"))
    )
    new ConnectionPool(
      new MySQLConnectionFactory(new Configuration(
        mySqlConf.getString("username"),
        mySqlConf.getString("host"),
        mySqlConf.getInteger("port"),
        mySqlConf.getString("password"),
        mySqlConf.getString("database"),
        new SSLConfiguration()
      )), poolConfiguration)
  }

  val qsSearchArea: Int => String = (limit: Int ) =>
    s"""
       |SELECT * FROM (SELECT store.storeId, store.name, astext(store.geo) as geo, store.currency,
       |store.email, store.zip, store.address, store.city, store.phone, store.open, store.close,
       |store.story, store.tz,
       |
       |standard_id, standard.ownerId, standard.gtin, standard.idCustom, standard.brand,
       |standard_name, standard.cond, standard.category, standard.price, standard.description, standard.url,
       |standard.hit, sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd, pathi, paths,
       |score,
       |
       |ST_Distance_Sphere(point(?, ?), geo) as distance_in_meters FROM store AS store
       |
       |JOIN inventory ON inventory.storeId = store.storeId
       |JOIN (SELECT standard.id AS standard_id, standard.ownerId, standard.gtin,
       |  standard.idCustom, standard.brand, standard.name AS standard_name, standard.cond, standard.category,
       |  standard.price, standard.description, standard.url, standard.hit,
       |  MATCH (standard.name, standard.category, standard.brand) AGAINST(?) as score FROM standard
       |  WHERE MATCH (standard.name, standard.category, standard.brand) AGAINST(?))  AS standard ON inventory.id = standard_id
       |
       |LEFT JOIN (SELECT standard_sale.id as sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd
       |  FROM standard_sale WHERE standard_sale.saleStart < ? AND standard_sale.saleEnd > ? ) AS standard_sale
       |  ON standard_id = sale_id
       |LEFT JOIN (SELECT img_standard.id, GROUP_CONCAT(img_standard.path ORDER BY ts ASC) pathi FROM img_standard GROUP BY img_standard.id) as img_standard ON img_standard.id = standard_id
       |LEFT JOIN (SELECT img_store.storeId, GROUP_CONCAT( img_store.path ORDER BY ts ASC) paths FROM img_store GROUP BY img_store.storeId) as img_store ON img_store.storeId = store.storeId
       |
       |WHERE ST_Within(geo, ST_Buffer(POINT(?, ?), 0.020)) ORDER BY distance_in_meters, score LIMIT $limit) AS d
     """.stripMargin


  val qsSearchStoreKeyword: Int => String = (limit: Int ) =>
    s"""
       |SELECT standard.*, standard_sale.*, store.currency, CONCAT(pathi) AS pathi, MATCH (standard.name, standard.category, standard.brand) AGAINST (?) as score FROM inventory
       |JOIN store ON inventory.storeId = store.storeId
       |JOIN standard ON inventory.id = standard.id
       |LEFT JOIN (SELECT standard_sale.id as sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd FROM standard_sale WHERE standard_sale.saleStart < ? AND standard_sale.saleEnd > ?) AS standard_sale ON standard.id = sale_id
       |LEFT JOIN (SELECT id, GROUP_CONCAT(img_standard.path ORDER BY ts) pathi FROM img_standard GROUP BY id) AS img_standard ON inventory.id = img_standard.id
       |WHERE inventory.storeId = ? AND MATCH (standard.name, standard.category, standard.brand) AGAINST (?) ORDER BY score DESC LIMIT $limit
     """.stripMargin

  val qsSearchStoreRandom: Int => String = (limit: Int) =>
    s"""
       |SELECT standard.*, standard_sale.*, store.currency, CONCAT(pathi) AS pathi FROM inventory
       |JOIN store ON inventory.storeId = store.storeId
       |JOIN standard ON inventory.id = standard.id
       |LEFT JOIN (SELECT standard_sale.id as sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd FROM standard_sale WHERE standard_sale.saleStart < ? AND standard_sale.saleEnd > ?) AS standard_sale ON standard.id = sale_id
       |LEFT JOIN (SELECT id, GROUP_CONCAT(img_standard.path ORDER BY ts ASC) pathi FROM img_standard GROUP BY id) AS img_standard ON inventory.id = img_standard.id
       |WHERE inventory.storeId = ? ORDER BY RAND() LIMIT $limit
     """.stripMargin


  val qsGetStores: Int => String = (limit: Int ) =>
    s"""
       |SELECT storeId, name, astext(geo) as geo, currency, email, zip, address, city, phone, open, close, story, tz, paths, distance_in_meters
       |FROM (SELECT store.*, CONCAT(paths) AS paths, ST_Distance_Sphere(point(?, ?), geo) as distance_in_meters FROM store AS store
       |LEFT JOIN (SELECT storeId, GROUP_CONCAT(path ORDER BY ts ASC) paths FROM img_store GROUP BY img_store.storeId) as img_store ON img_store.storeId = store.storeId) AS img_store
       |WHERE ST_Within(geo, ST_Buffer(POINT(?, ?), 0.020)) ORDER BY distance_in_meters LIMIT $limit
     """.stripMargin

  val qsGetStore: String =
    s"""
       |SELECT storeId, name, astext(geo) as geo, currency, email, zip, address, city, phone, open, close, story, tz, paths FROM store
       |LEFT JOIN (SELECT storeId AS store_id, GROUP_CONCAT(path ORDER BY ts ASC) paths FROM img_store GROUP BY img_store.storeId)  AS img_store ON img_store.store_id = store.storeId
       |WHERE storeId = ?
     """.stripMargin

  def saleFrom(r: ArrayRowData, saleIdAlias: String = "sale_id"): Option[Sale] = {
    if (r.get(saleIdAlias) != null) {
      Some(Sale(r.getString(saleIdAlias), stringPrice(r.get(TableStandardSale.SALE_PRICE).asInstanceOf[java.math.BigDecimal]), r.getLong(TableStandardSale.SALE_START), r.getLong(TableStandardSale.SALE_END)))
    } else {
      None
    }
  }

  def imgFrom(r: ArrayRowData, imgAlias: String, imgHost: String, root: Root): Option[Img] =
    Option(r.getString(imgAlias)) match {
      case Some(s) => Some(Img(s.split(",").toList, imgHost, root))
      case _ => None
    }

  def itemFrom(r: ArrayRowData, sale: Option[Sale], img: Option[Img],  standardIdAlias: String, standardNameAlias: String): Item =
    Item(r.getString(standardIdAlias), r.getInt(TableStandard.OWNER_ID),
      r.getString(TableStandard.ID_CUSTOM), r.getString(TableStandard.GTIN), r.getString(standardNameAlias), stringPrice(r.get(TableStandard.PRICE).asInstanceOf[java.math.BigDecimal]), r.getString(TableStandard.CATEGORY),
      r.getString(TableStandard.BRAND), Item.findCondition(r.getString(TableStandard.COND)).getOrElse(Condition.NEW), r.getString(TableStandard.DESCRIPTION), r.getString(TableStandard.URL),
      img.orNull, r.getInt(TableStandard.HIT), sale, None)

  def storeFrom(r: ArrayRowData, img: Option[Img], distance: Option[Double], geoAlias: String = "geo"): Store ={
    val lnglat = Store.toLngLat(r.getString(geoAlias))
    Store(r.getInt(TableStore.STORE_ID), r.getString(TableStore.NAME), r.getString(TableStore.ZIP), r.getString(TableStore.ADDRESS),
      r.getString(TableStore.EMAIL), r.getString(TableStore.PHONE), lnglat.head, lnglat(1),
      r.getString(TableStore.OPEN), r.getString(TableStore.CLOSE), distance, r.getString(TableStore.STORY),
      r.getString(TableStore.CURRENCY), r.getByte(TableStore.TZ).toString.toInt, r.getString(TableStore.CITY), img.orNull)
  }

  def stringPrice(price: java.math.BigDecimal): String = BigDecimal(price).setScale(2, RoundingMode.HALF_EVEN).bigDecimal.toPlainString

}

class Repository(client: ConnectionPool[MySQLConnection], queryCfg: JsonObject, imgHost: String) {
  val limit: Integer = queryCfg.getInteger("limit")

  def terminate(): Future[Connection] = FutureConverters.toScala(client.disconnect())

  def testConnection(): Future[Repository] =  {
    for {
      fcon <- FutureConverters.toScala(client.connect())
      fque <- FutureConverters.toScala(fcon.sendQuery("SELECT 'hello'"))
      ffin <-
        if (fque.getRows.get(0).get(0).toString.equals("hello")){
          Future.successful(this)
        } else {
          Future.failed(new Throwable("Test query failed"))
        }
    } yield ffin
  }

  def search(kwd: String, lat: Double, lng: Double, legitSaleTs: Long, rootCategory: Option[String]): Future[MtpResponse[IPP]] = {
    val params = Seq(
      lng,
      lat,
      kwd,
      kwd,
      legitSaleTs,
      legitSaleTs,
      lng,
      lat
    )
    // rootCategory is from android dropdown-specify search by root category.
    val sql = Repository.qsSearchArea(limit)
    for {
      fque <- FutureConverters.toScala(client.sendPreparedStatement(sql, params.asJava))
      fpar <- Future {
        val res = fque.getRows.stream().toArray().map(_.asInstanceOf[ArrayRowData]).map(r => {
          val imgItem  = Repository.imgFrom(r, "pathi", imgHost, Root.i)
          val sale     = Repository.saleFrom(r)
          val item     = Repository.itemFrom(r, sale, imgItem, "standard_id", "standard_name")
          val imgStore = Repository.imgFrom(r, "paths", imgHost, Root.store)
          val distance = Some(r.getDouble("distance_in_meters").doubleValue())
          val store    = Repository.storeFrom(r, imgStore, distance)
          IPP(item, store)
        }).toList
        MtpResponse.success(res)
      }
    } yield fpar

  }

  def getStores(lat: Double, lng: Double): Future[MtpResponse[Store]] = {
    val params = Seq(lng, lat, lng, lat)
    val sql    = Repository.qsGetStores(limit)
    for {
      fque <- FutureConverters.toScala(client.sendPreparedStatement(sql, params.asJava))
      fpar <- Future {
        val res = fque.getRows.stream().toArray().map(_.asInstanceOf[ArrayRowData]).map(r => {
          val imgStores = Repository.imgFrom(r, "paths", imgHost, Root.store)
          val distance  = Some(r.getDouble("distance_in_meters").doubleValue())
          Repository.storeFrom(r, imgStores, distance)
        }).toList
        MtpResponse.success(res)
      }
    } yield fpar
  }

  def getStore(storeId: Int): Future[MtpResponse[Store]] = {
    val params = Seq(storeId)
    val sql    = Repository.qsGetStore
    for {
      fque <- FutureConverters.toScala(client.sendPreparedStatement(sql, params.asJava))
      fpar <- Future {
        val res = fque.getRows.stream().toArray().map(_.asInstanceOf[ArrayRowData]).map(r => {
          val imgStores = Repository.imgFrom(r, "paths", imgHost, Root.store)
          val distance  = None
          Repository.storeFrom(r, imgStores, distance)
        }).toList
        MtpResponse.success(res)
      }
    } yield fpar
  }


  def searchStoreKeyword(kwd: String, storeId: Int, legitSaleTs: Long): Future[MtpResponse[Item]] = {
    val params = Seq(kwd, legitSaleTs, legitSaleTs, storeId, kwd)
    val sql    = Repository.qsSearchStoreKeyword(limit)
    searchStoreFut(sql, params)
  }

  def searchStoreRandom(storeId: Int, legitSaleTs: Long): Future[MtpResponse[Item]] = {
    val params = Seq(legitSaleTs, legitSaleTs, storeId)
    val sql    = Repository.qsSearchStoreRandom(limit)
    searchStoreFut(sql, params)
  }

  private def searchStoreFut(sql: String, params: Seq[Any]): Future[MtpResponse[Item]] = {
    for {
      fque <- FutureConverters.toScala(client.sendPreparedStatement(sql, params.asJava))
      fpar <- Future {
        val res = fque.getRows.stream().toArray().map(_.asInstanceOf[ArrayRowData]).map(r => {
          val imgItem = Repository.imgFrom(r, "pathi", imgHost, Root.i)
          val sale    = Repository.saleFrom(r)
          Repository.itemFrom(r, sale, imgItem, TableStandard.ID, TableStandard.NAME)
        }).toList
        MtpResponse.success(res)
      }
    } yield fpar
  }


}