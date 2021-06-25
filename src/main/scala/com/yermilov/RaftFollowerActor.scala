package com.yermilov

import java.util.concurrent.TimeUnit

import com.yermilov.RaftActorMessages.ElectionCompleted
import com.yermilov.raft.raft.RequestVoteRequest
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait RaftFollowerActor {
  this: RaftActor =>
  def electionCompleted(result: ElectionResult): Unit = {
    logger.info(s"Node ${state.id} completed election with result $result")
    result match {
      case Left(term) => becomeFollower(Some(term._1), term._2)
      case Right(votes) if votes > state.otherNodes.length / 2 && state.mode == Candidate => becomeLeader()
      case _ => ()
    }
  }

  def startElection(): Unit =
    if (state.mode != Leader && (state.lastMessageFromLeader.isEmpty || DateTime.now().getMillis - state.lastMessageFromLeader.get.getMillis > this.electionTimer.toMillis)) {
      state.currentTerm += 1
      state.mode = Candidate
      state.votedFor = Option(state.id)

      logger.info(s"Node ${state.id} starts election, it's term is ${state.currentTerm}")

      val calls: Seq[Future[ElectionResponse]] = state.otherNodes.map {
        conn =>
          conn.connection.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).requestVoteRpc(RequestVoteRequest(
            state.currentTerm,
            state.id,
            state.lastLogTerm,
            state.lastLogIndex
          )).map {
            resp =>
              if (resp.term > state.currentTerm) Left(resp.term, conn.id) else Right(resp.voteGranted)
          }.recover {
            case err =>
              logger.error(s"Sending RequestVote to node $conn failed because of $err")
              Right(false)
          }

      }

      logger.info(s"Node ${state.id} waiting for responses from ${calls.length} nodes")

      val zero: ElectionResult = Right(0)
      Future.foldLeft(calls)(zero) {
        (votes: ElectionResult, resp: ElectionResponse) =>
          resp match {
            case Left(term) => Left(term._1, term._2)
            case Right(granted) =>
              logger.info(granted)
              votes.map(_ + (if (granted) 1 else 0))
          }
      } onComplete {
        case Success(result) => self ! ElectionCompleted(result)
        case Failure(exception) => logger.error(s"Voting for ${state.id} resulted in $exception")
      }
    }

  def followerHeartbeat(): Unit = {
    //logger.info("Own heartbeat received.")
  }

}
