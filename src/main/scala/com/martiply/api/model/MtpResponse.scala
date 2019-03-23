package com.martiply.api.model

import java.util

import com.jsoniter.annotation.JsonProperty
import com.martiply.model.interfaces.IMtpResponse

import scala.collection.JavaConverters._

object MtpResponse {

  def apply[A](success: Boolean, error: String, data:Option[List[A]]): MtpResponse[A] = {
     new MtpResponse[A](success, error, if (data.isDefined) data.get.asJava else null)
  }

  def success[A](data: List[A]): MtpResponse[A] = MtpResponse[A](success = true, error = null, Some(data))

  def error(error: String): MtpResponse[Nothing] = MtpResponse[Nothing](success = false, error, None)

}

class MtpResponse[A](
     success: Boolean,
    @JsonProperty(defaultValueToOmit = "null") error: String,
    @JsonProperty(nullable = true) data: util.List[A]
  ) extends IMtpResponse {

  override def isSuccess: Boolean = success

  def getError: String = error

  def getData: util.List[A] = data

}
