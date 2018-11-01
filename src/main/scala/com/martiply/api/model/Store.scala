package com.martiply.api.model

import com.jsoniter.annotation.JsonProperty
import com.martiply.model.interfaces.{AbsImg, IStore}

object Store {
  def apply(storeId: Int, name: String, zip: String, address: String, email: String, phone: String, lng: Double, lat: Double, open: String, close: String, distance: Option[Double],
            story: String, currency: String, tz: Int, city: String, img: Img): Store = new Store(storeId, name, zip, address, email, phone, lng, lat, open, close, distance.getOrElse(-1d), story, currency, tz, city, img)

  //POINT(107.584811 -6.892545) lng, lat
  def toLngLat(pointText: String): Seq[Double] = pointText.replace("POINT(", "").replace(")", "").split(" ").map(_.toDouble)

}

class Store(storeId: Int, name: String, zip: String, address: String, email: String, phone: String, lng: Double, lat: Double, open: String, close: String, distance: Double,
            story: String, currency: String, tz: Int, city: String, img: Img) extends IStore {


  override def getStoreId: Int = storeId

  override def getName: String = name

  override def getZip: String = zip

  override def getAddress: String = address

  override def getPhone: String = phone

  override def getLng: Double = lng

  override def getLat: Double = lat

  override def getOpen: String = open

  override def getClose: String = close

  override def getDistance: Double = distance

  override def getStory: String = story

  override def getCurrency: String = currency

  override def getTz: Int = tz

  override def getCity: String = city

  override def getImg: AbsImg = img

  override def getEmail: String = email
}
