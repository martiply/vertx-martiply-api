package com.martiply.api

import java.util.concurrent.TimeUnit

import com.github.jasync.sql.db.general.ArrayRowData
import com.github.jasync.sql.db.mysql.MySQLConnection
import com.github.jasync.sql.db.mysql.pool.MySQLConnectionFactory
import com.github.jasync.sql.db.pool.{ConnectionPool, PoolConfiguration}
import com.github.jasync.sql.db.{Configuration, Connection, SSLConfiguration}
import com.martiply.api.model._
import com.martiply.model.interfaces.IItem.{Category, Condition, IdType}
import com.martiply.table._
import io.vertx.core.json.JsonObject

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Repository {
  def apply(mySqlConf: JsonObject): Repository = {
    val con = buildClient(mySqlConf)
    new Repository(con, mySqlConf.getJsonObject("query"))
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
       |standard_id, standard.ownerId, standard.idType, standard.gtin, standard.idCustom, standard.brand,
       |standard_name, standard.cond, standard.category, standard.price, standard.description, standard.url,
       |standard.hit, sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd, urli, urls,
       |score,
       |
       |ST_Distance_Sphere(point(?, ?), geo) as distance_in_meters FROM store AS store
       |
       |JOIN inventory ON inventory.storeId = store.storeId
       |JOIN (SELECT standard.id AS standard_id, standard.ownerId, standard.idType, standard.gtin,
       |  standard.idCustom, standard.brand, standard.name AS standard_name, standard.cond, standard.category,
       |  standard.price, standard.description, standard.url, standard.hit,
       |  MATCH (standard.name, standard.category, standard.brand) AGAINST(?) as score FROM standard
       |  WHERE MATCH (standard.name, standard.category, standard.brand) AGAINST(?))  AS standard ON inventory.id = standard_id
       |
       |LEFT JOIN (SELECT standard_sale.id as sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd
       |  FROM standard_sale WHERE standard_sale.saleStart < ? AND standard_sale.saleEnd > ? ) AS standard_sale
       |  ON standard_id = sale_id
       |LEFT JOIN (SELECT img_standard.id, GROUP_CONCAT(img_standard.url) urli FROM img_standard GROUP BY img_standard.id) as img_standard ON img_standard.id = standard_id
       |LEFT JOIN (SELECT img_store.storeId, GROUP_CONCAT( img_store.url) urls FROM img_store GROUP BY img_store.storeId) as img_store ON img_store.storeId = store.storeId
       |
       |WHERE ST_Within(geo, ST_Buffer(POINT(?, ?), 0.020)) ORDER BY distance_in_meters, score LIMIT $limit) AS d
     """.stripMargin


  val qsSearchStoreKeyword: Int => String = (limit: Int ) =>
    s"""
       |SELECT standard.*, standard_sale.*, store.currency, CONCAT(urli) AS urli, MATCH (standard.name, standard.category, standard.brand) AGAINST (?) as score  FROM inventory
       |JOIN store ON inventory.storeId = store.storeId
       |JOIN standard ON inventory.id = standard.id
       |LEFT JOIN (SELECT standard_sale.id as sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd FROM standard_sale WHERE standard_sale.saleStart < ? AND standard_sale.saleEnd > ?) AS standard_sale ON standard.id = sale_id
       |LEFT JOIN (SELECT id, GROUP_CONCAT(img_standard.url) urli FROM img_standard GROUP BY id) AS img_standard ON inventory.id = img_standard.id
       |WHERE inventory.storeId = ? AND MATCH (standard.name, standard.category, standard.brand) AGAINST (?) ORDER BY score DESC LIMIT $limit
     """.stripMargin

  val qsSearchStoreRandom: Int => String = (limit: Int) =>
    s"""
       |SELECT standard.*, standard_sale.*, store.currency, CONCAT(urli) AS urli FROM inventory
       |JOIN store ON inventory.storeId = store.storeId
       |JOIN standard ON inventory.id = standard.id
       |LEFT JOIN (SELECT standard_sale.id as sale_id, standard_sale.salePrice, standard_sale.saleStart, standard_sale.saleEnd FROM standard_sale WHERE standard_sale.saleStart < ? AND standard_sale.saleEnd > ?) AS standard_sale ON standard.id = sale_id
       |LEFT JOIN (SELECT id, GROUP_CONCAT(img_standard.url) urli FROM img_standard GROUP BY id) AS img_standard ON inventory.id = img_standard.id
       |WHERE inventory.storeId = ? ORDER BY RAND() LIMIT $limit
     """.stripMargin


  val qsGetStores: Int => String = (limit: Int ) =>
    s"""
       |SELECT storeId, name, astext(geo) as geo, currency, email, zip, address, city, phone, open, close, story, tz, urls, distance_in_meters
       |FROM (SELECT store.*, CONCAT(urls) AS urls, ST_Distance_Sphere(point(?, ?), geo) as distance_in_meters FROM store AS store
       |LEFT JOIN (SELECT storeId, GROUP_CONCAT(url) urls FROM img_store GROUP BY img_store.storeId) as img_store ON img_store.storeId = store.storeId) AS img_store
       |WHERE ST_Within(geo, ST_Buffer(POINT(?, ?), 0.020)) ORDER BY distance_in_meters LIMIT $limit
     """.stripMargin

  val qsGetStore: String =
    s"""
       |SELECT storeId, name, astext(geo) as geo, currency, email, zip, address, city, phone, open, close, story, tz, urls FROM store
       |LEFT JOIN (SELECT storeId AS store_id, GROUP_CONCAT(url) urls FROM img_store GROUP BY img_store.storeId)  AS img_store ON img_store.store_id = store.storeId
       |WHERE storeId = ?
     """.stripMargin

  def saleFrom(r: ArrayRowData, saleIdAlias: String = "sale_id"): Option[Sale] = {
    if (r.get(saleIdAlias) != null) {
      Some(Sale(r.get(saleIdAlias).asInstanceOf[String], r.get(TableStandardSale.SALE_PRICE).asInstanceOf[Float], r.get(TableStandardSale.SALE_START).asInstanceOf[Long], r.get(TableStandardSale.SALE_END).asInstanceOf[Long]))
    } else {
      None
    }
  }

  def imgFrom(r: ArrayRowData, imgAlias: String): Option[Img] =
    Option(r.get(imgAlias)) match {
      case Some(s) => Some(Img(s.asInstanceOf[String].split(",").toList))
      case _ => None
    }

  def itemFrom(r: ArrayRowData, sale: Option[Sale], img: Option[Img],  standardIdAlias: String, standardNameAlias: String): Item =
    Item(r.get(standardIdAlias).asInstanceOf[String], r.get(TableStandard.OWNER_ID).asInstanceOf[Int], Item.findIdType(r.get(TableStandard.ID_TYPE).asInstanceOf[String]).getOrElse(IdType.custom),
      r.get(TableStandard.ID_CUSTOM).asInstanceOf[String], r.get(TableStandard.GTIN).asInstanceOf[String], r.get(standardNameAlias).asInstanceOf[String], Item.findCategory(r.get(TableStandard.CATEGORY).asInstanceOf[String]).getOrElse(Category.product),
      r.get(TableStandard.BRAND).asInstanceOf[String], Item.findCondition(r.get(TableStandard.COND).asInstanceOf[String]).getOrElse(Condition.NEW), r.get(TableStandard.DESCRIPTION).asInstanceOf[String], r.get(TableStandard.URL).asInstanceOf[String],
      img.orNull, r.get(TableStandard.HIT).asInstanceOf[Int], sale, None)

  def storeFrom(r: ArrayRowData, img: Option[Img], distance: Option[Double], geoAlias: String = "geo"): Store ={
    val lnglat = r.get(geoAlias).asInstanceOf[String]
    Store(r.get(TableStore.STORE_ID).asInstanceOf[Int], r.get(TableStore.NAME).asInstanceOf[String], r.get(TableStore.ZIP).asInstanceOf[String], r.get(TableStore.ADDRESS).asInstanceOf[String],
      r.get(TableStore.EMAIL).asInstanceOf[String], r.get(TableStore.PHONE).asInstanceOf[String], lnglat.head, lnglat(1),
      r.get(TableStore.OPEN).asInstanceOf[String], r.get(TableStore.CLOSE).asInstanceOf[String], distance, r.get(TableStore.STORY).asInstanceOf[String],
      r.get(TableStore.CURRENCY).asInstanceOf[String], r.get(TableStore.TZ).asInstanceOf[Byte].toString.toInt, r.get(TableStore.CITY).asInstanceOf[String], img.orNull)
  }

}

class Repository(client: ConnectionPool[MySQLConnection], queryCfg: JsonObject) {
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

  def search(kwd: String, lat: Double, lng: Double, legitSaleTs: Long, category: Option[Category]): Future[MtpResponse[IPP]] = {
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
    val sql = Repository.qsSearchArea(limit)
    for {
      fque <- FutureConverters.toScala(client.sendPreparedStatement(sql, params.asJava))
      fpar <- Future {
        val res = fque.getRows.stream().toArray().map(_.asInstanceOf[ArrayRowData]).map(r => {
          val imgItem  = Repository.imgFrom(r, "urli")
          val sale     = Repository.saleFrom(r)
          val item     = Repository.itemFrom(r, sale, imgItem, "standard_id", "standard_name")
          val imgStore = Repository.imgFrom(r, "urls")
          val distance = Some(r.get("distance_in_meters").asInstanceOf[Double])
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
          val imgStores = Repository.imgFrom(r, "urls")
          val distance  = Some(r.get("distance_in_meters").asInstanceOf[Double])
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
        fque.getRows.columnNames().forEach(println)
        val res = fque.getRows.stream().toArray().map(_.asInstanceOf[ArrayRowData]).map(r => {

          val imgStores = Repository.imgFrom(r, "urls")
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
          val imgItem = Repository.imgFrom(r, "urli")
          val sale    = Repository.saleFrom(r)
          Repository.itemFrom(r, sale, imgItem, TableStandard.ID, TableStandard.NAME)
        }).toList
        MtpResponse.success(res)
      }
    } yield fpar
  }


}