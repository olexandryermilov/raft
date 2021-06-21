package com.yermilov

import java.util.logging.Logger

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.yermilov.raft.raft._
import io.grpc.{Server, ServerBuilder}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * [[https://github.com/grpc/grpc-java/blob/v0.15.0/examples/src/main/java/io/grpc/examples/helloworld/HelloWorldServer.java]]
 */
object RaftServer {
  private val logger = Logger.getLogger(classOf[RaftServer].getName)
  implicit val executionContext = ExecutionContext.global
  val actorSystem = ActorSystem("RaftSystem")

  def main(args: Array[String]): Unit = {
    val server = new RaftServer(ExecutionContext.global)
    server.start(id = args.head.toInt, otherPorts = args.tail.map(_.toInt))
    server.blockUntilShutdown()
  }

  private val port = findFreePort()
}

class RaftServer(executionContext: ExecutionContext) {
  self =>
  private[this] var server: Server = null
  private[this] var actor: ActorRef = null

  private def start(id: Int, otherPorts: Seq[Int]): Unit = {
    val state = NodeState(id, otherNodes = otherPorts, Follower)
    actor = RaftServer.actorSystem.actorOf(Props(classOf[RaftActor], state))
    server = ServerBuilder.forPort(RaftServer.port).addService(RaftGrpc.bindService(new RaftService(actor), executionContext)).build.start
    RaftServer.logger.info("Server started, listening on " + RaftServer.port)
    sys.addShutdownHook {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      self.stop()
      System.err.println("*** server shut down")
    }
  }

  private def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  private class RaftService(actorRef: ActorRef) extends RaftGrpc.Raft {
    implicit val timeout = Timeout(1 seconds)

    override def raftEcho(req: RaftRequest): Future[RaftResponse] = {
      val reply = RaftResponse(req.id1)
      Future.successful(reply)
    }

    override def requestVoteRpc(request: RequestVoteRequest): Future[RequestVoteResponse] =
      (actor ? RaftActorMessages.RequestVote(request)).asInstanceOf[Future[RequestVoteResponse]]

    override def appendEntriesRpc(request: AppendEntriesRequest): Future[AppendEntriesResponse] =
      (actor ? RaftActorMessages.AppendLog(request)).asInstanceOf[Future[AppendEntriesResponse]]
  }

}
