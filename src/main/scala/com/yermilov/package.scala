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

  type HeartbeatResult = Either[(Int, Int), List[(Int, Int)]]
  type Term = Int
  type NodeId = Int
  type Votes = Int
  type VoteGranted = Boolean
  //type Term = Int

  type ElectionResult = Either[(Term, NodeId), Votes]
  type ElectionResponse = Either[(Term, NodeId), VoteGranted]
}
