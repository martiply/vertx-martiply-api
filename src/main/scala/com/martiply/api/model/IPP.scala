package com.martiply.api.model

import com.martiply.model.interfaces.{IIPP, IItem, IStore}

object IPP {
  def apply(item: Item, store: Store): IPP = new IPP(item, store)

}

class IPP(item: Item, store: Store) extends IIPP{

  override def getItem: IItem = item

  override def getStore: IStore = store
}
