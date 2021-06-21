package com.yermilov

import com.yermilov.raft.raft.{RaftGrpc, RaftRequest, RaftResponse}
import io.grpc.ManagedChannelBuilder

import scala.concurrent.Await
import scala.concurrent.duration._

object RaftClient {
  def main(args: Array[String]) = {
    val channel = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext().build
    val request = RaftRequest(1)
    val blockingStub = RaftGrpc.stub(channel)
    val reply: RaftResponse = Await.result(blockingStub.raftEcho(request), 5.seconds)
    println(reply)
  }
}


