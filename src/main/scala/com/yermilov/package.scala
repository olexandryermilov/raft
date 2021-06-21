package com

import java.net.ServerSocket

import scala.util.{Failure, Success, Try}

package object yermilov {

  def findFreePort(): Int = {
    Try {
      val socket = new ServerSocket(0)
      socket.setReuseAddress(true)
      val port = socket.getLocalPort
      socket.close()
      port
    } match {
      case Failure(exception) => throw exception
      case Success(value) => value
    }
  }
}
