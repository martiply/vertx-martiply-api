package com.martiply.api.model

import java.util

import com.martiply.model.interfaces.AbsImg

object Img {
  import scala.collection.JavaConverters._

  def apply(urls: List[String]): Img = new Img(urls.asJava)


}

class Img(urls: util.List[String]) extends AbsImg {

  override def getUrls: util.List[String] = urls

}
