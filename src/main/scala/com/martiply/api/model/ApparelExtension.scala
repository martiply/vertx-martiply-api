package com.martiply.api.model

import com.martiply.model.interfaces.IApparelExtension
import com.martiply.model.interfaces.IApparelExtension.{Age, Gender, SizeSystem}


class ApparelExtension(id: String, groupId: String, gender: Gender, age: Age, sizeSystem: SizeSystem, size: String, color: String, material: String, feature: String ) extends IApparelExtension{

  override def getId: String = id

  override def getGroupId: String = groupId

  override def getGender: IApparelExtension.Gender = gender

  override def getAge: IApparelExtension.Age = age

  override def getSizeSystem: IApparelExtension.SizeSystem = sizeSystem

  override def getSize: String = size

  override def getColor: String = color

  override def getMaterial: String = material

  override def getFeature: String = feature
}

