package com.martiply.api.model

import com.martiply.model.interfaces.IOwner

class Owner(ownerId: Int, name: String, email: String, phone: String) extends IOwner {

  override def getOwnerId: Int = ownerId

  override def getEmail: String = email

  override def getName: String = name

  override def getPhone: String = phone
}
