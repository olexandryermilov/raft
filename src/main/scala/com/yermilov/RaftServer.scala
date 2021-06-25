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
  private var port: Int = 0 //findFreePort()

  def main(args: Array[String]): Unit = {
    println(args.mkString(" "))
    port = 50000 + args.head.toInt
    val server = new RaftServer(ExecutionContext.global)
    server.start(id = args.head.toInt, otherPorts = Seq(50001, 50002, 50003, 50004, 50005).filter(_ != port))
    server.blockUntilShutdown()
  }

}

class RaftServer(executionContext: ExecutionContext) {
  self =>
  private[this] var server: Server = null
  private[this] var actor: ActorRef = null

  private def start(id: Int, otherPorts: Seq[Int]): Unit = {
    val state = NodeState(id, otherNodes = otherPorts.map(port => Node(RaftClient.createStub(port), port - 50000)), Follower)
    actor = RaftServer.actorSystem.actorOf(Props(classOf[RaftActor], state))
    println(actor.path)
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
    import scala.language.postfixOps
    implicit val timeout = Timeout(1.seconds)

    private val logger = Logger.getLogger(classOf[RaftService].getName)

    override def raftEcho(req: RaftRequest): Future[RaftResponse] = {
      val reply = RaftResponse(req.id1)
      Future.successful(reply)
    }

    override def requestVoteRpc(request: RequestVoteRequest): Future[RequestVoteResponse] = {
      logger.info(request.toString)
      (actor ? RaftActorMessages.RequestVote(request)).asInstanceOf[Future[RequestVoteResponse]]
    }

    override def appendEntriesRpc(request: AppendEntriesRequest): Future[AppendEntriesResponse] =
      (actor ? RaftActorMessages.AppendLog(request)).asInstanceOf[Future[AppendEntriesResponse]]

    override def leaderHeartbeat(request: LeaderHeartbeatRequest): Future[LeaderHeartbeatResponse] =
      (actor ? RaftActorMessages.LeaderHeartbeat()).asInstanceOf[Future[LeaderHeartbeatResponse]]
  }

}
