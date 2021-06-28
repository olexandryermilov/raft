package com.yermilov

import java.util.concurrent.TimeUnit

import com.yermilov.RaftActorMessages.{AppendLog, HeartbeatSuccess, LeaderHeartbeat, LeaderHeartbeatTick, OwnHeartbeat}
import com.yermilov.raft.raft.{AppendEntriesRequest, RaftGrpc}

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait RaftLeaderActor {
  this: RaftActor =>

  def heartbeat(): Any = {
    val calls = state.otherNodes.map {
      conn: Node =>
        val nextIndex: Int = state.nextIndex getOrElse(conn.id, 0)
        val (prevLog, entries) = state.log.span(_.index < nextIndex)

        val prevLogIndex = if (prevLog.nonEmpty) prevLog.last.index else 0
        val prevLogTerm = if (prevLog.nonEmpty) prevLog.last.term else 0

        conn.connection.withDeadlineAfter(500, TimeUnit.MILLISECONDS).appendEntriesRpc(AppendEntriesRequest(
          leaderId = state.id,
          term = state.currentTerm,
          entries = entries,
          prevLogIndex = prevLogIndex,
          prevLogTerm = prevLogTerm,
          leaderCommit = state.commitIndex
        )).map {
          resp => {
            val nextIndexUpdate = if (entries.nonEmpty) {
              if (resp.success) entries.last.index + 1 else nextIndex - 1
            } else nextIndex

            if (resp.currentTerm > state.currentTerm) Left(resp.currentTerm, conn.id) else Right(conn.id -> nextIndexUpdate)
          }
        }.recover {
          case exception =>
            logger.info(s"Error occurred while sending heartbeat $exception")
            Right(conn.id -> nextIndex)
        }
    }

    val zero: HeartbeatResult = Right(List.empty)
    val successF: Future[HeartbeatResult] = Future.foldLeft(calls.toList)(zero) {
      (acc, resp) =>
        resp.flatMap(value => acc.map(_ :+ value))
    }

    successF onComplete {
      case Success(value) => self ! RaftActorMessages.HeartbeatSuccess(value)
      case Failure(exception) => logger.error(s"Heartbeat for server ${state.id} finished with $exception")
    }
  }

  def heartbeatCompleted(result: Either[(Int, Int), List[(Int, Int)]]): Unit = result match {
    case Left(termWithServer) => becomeFollower(Some(termWithServer._1), termWithServer._2)
    case Right(nextIndexes) =>
      logger.info(s"Node ${state.id} successfully sent heartbeat with term=${state.currentTerm}")
      logger.info(s"Nextindex: ${state.nextIndex}, matchindex ${state.matchIndex} before update")
      state.nextIndex = state.nextIndex ++ nextIndexes
      state.matchIndex = state.matchIndex ++ nextIndexes.filter {
        case (serverId: Int, index: Int) => index > state.matchIndex.getOrElse(serverId, 0) // increase only
      }.map(x => x._1 -> (x._2 - 1))
      logger.info(s"Nextindex: ${state.nextIndex}, matchindex ${state.matchIndex} after update")
      val commitIndex = (state.commitIndex until state.log.length).findLast(cI => {
        if (state.log(cI).term == state.currentTerm) {
          val matchCount = 1 + state.matchIndex.count(mI => mI._2 >= cI)
          matchCount * 2 > state.otherNodes.length
        } else false
      }).getOrElse(state.commitIndex)
      logger.info(s"New commit index is $commitIndex")
      state.commitIndex = commitIndex
  }

  def becomeLeader(): Unit = {
    logger.info(s"Node ${state.id} became a leader")
    timers.startTimerAtFixedRate(LeaderHeartbeatTick, LeaderHeartbeat, heartBeatTimer)
    state.mode = Leader
    state.leader = Some(state.id)
    state.lastMessageFromLeader = None
    state.otherNodes.foreach(node => state.nextIndex.put(node.id, state.lastLogIndex + 1))
    state.otherNodes.foreach(node => state.matchIndex.put(node.id, 0))

    heartbeat()
    //context.become(receiveLeader)
  }

  def receiveLeader: Receive = {
    case OwnHeartbeat => heartbeat()

  }
}
