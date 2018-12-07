package com.martiply.api.model

import com.martiply.model.interfaces.ISale


object Sale {
  def apply(id: String, salePrice: String, saleStart: Long, saleEnd: Long): Sale = new Sale(id, salePrice, saleStart, saleEnd)

}

class Sale(id: String, salePrice: String, saleStart: Long, saleEnd: Long) extends ISale {

  override def getId: String = id

  override def getSalePrice: String = salePrice

  override def getSaleStart: Long = saleStart

  override def getSaleEnd: Long = saleEnd
}
