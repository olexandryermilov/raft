package com.yermilov

import com.yermilov.raft.raft.RaftGrpc.Raft
import com.yermilov.raft.raft.{AppendLogToLeaderRequest, GetLeaderRequest, GetNodeStateRequest, RaftGrpc, RaftRequest, RaftResponse}
import io.grpc.ManagedChannelBuilder

import scala.concurrent.Await
import scala.concurrent.duration._

object RaftClient {

  def createStub(port: Int): RaftGrpc.RaftStub = {
    val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build
    RaftGrpc.stub(channel)
  }

  def main(args: Array[String]) = {
    val channel = ManagedChannelBuilder.forAddress("localhost", 50001).usePlaintext().build
    val request = AppendLogToLeaderRequest("Log entry 10")//GetLeaderRequest()
    val blockingStub: RaftGrpc.RaftStub = RaftGrpc.stub(channel)
    //val reply = Await.result(blockingStub.getNodeState(request),5.seconds)//.appendLogToLeader(request), 5.seconds)
    val reply = Await.result(blockingStub.appendLogToLeader(request),5.seconds)//.appendLogToLeader(request), 5.seconds)
    println(reply)
  }
}


