package com.martiply.api.model

import com.jsoniter.annotation.JsonProperty
import com.martiply.model.interfaces.IItem.{Category, Condition, IdType}
import com.martiply.model.interfaces.{AbsImg, IApparelExtension, IItem, ISale}

object Item {

  def apply(id: String, ownerId: Int, idType: IdType, idCustom: String, gtin: String, name: String, price: String, category: Category, brand: String,
            condition: Condition, description: String, url: String, img: Img, hits: Int, sale: Option[Sale], apparelExtension: Option[ApparelExtension]): Item =
    new Item(id, ownerId, idType, idCustom, gtin, name, price, category, brand, condition, description, url, img, hits, sale.orNull, apparelExtension.orNull)


  def findCategory(in: String) : Option[Category] = Category.values().find(p => in.startsWith(p.toString))

  def findIdType(in: String) : Option[IdType] = IdType.values().find(_.toString == in)

  def findCondition(in: String): Option[Condition] = Condition.values().find(_.toString == in)


}

class Item(id: String, ownerId: Int, idType: IdType, idCustom: String, gtin: String, name: String, price: String, category: Category, brand: String,
           condition: Condition, description: String, url: String, img: Img, hits: Int,
           sale: Sale,
           @JsonProperty(defaultValueToOmit = "null") apparelExtension: ApparelExtension) extends IItem{

  override def getId: String = id

  override def getOwnerId: Int = ownerId

  override def getIdCustom: String = idCustom

  override def getGtin: String = gtin

  override def getDescription: String = description

  override def getName: String = name

  override def getCategory: IItem.Category = category

  override def getBrand: String = brand

  override def getIdType: IItem.IdType = idType

  override def getHits: Int = hits

  override def getCondition: IItem.Condition = condition

  override def getApparelExtension: IApparelExtension = apparelExtension

  override def getUrl: String = url

  override def getSale: ISale = sale

  override def getImg: AbsImg = img

  override def getPrice: String = price
}
