package com.martiply.api.model

import java.util

import com.martiply.model.interfaces.AbsImg
import com.martiply.model.interfaces.AbsImg.Root

object Img {
  import scala.collection.JavaConverters._

  def apply(urls: List[String], imgHost: String, root: Root): Img = new Img(urls.asJava, imgHost, root)

}

class Img(paths: util.List[String], imgHost: String, root: Root) extends AbsImg {

  override def getPaths: util.List[String] = paths

  override def getImgHost: String = imgHost

  override def getRoot: AbsImg.Root = root

}
