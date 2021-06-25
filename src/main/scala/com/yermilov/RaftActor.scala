package com.yermilov

import akka.actor.{Actor, Timers}
import com.yermilov.raft.raft.{AppendEntriesRequest, AppendEntriesResponse, LeaderHeartbeatRequest, LeaderHeartbeatResponse, LogEntry, RequestVoteRequest, RequestVoteResponse}
import org.joda.time.DateTime
import wvlet.log.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object RaftActorMessages {

  case class AppendLog(request: AppendEntriesRequest)

  case class RequestVote(requestVote: RequestVoteRequest)

  case class HeartbeatSuccess(result: HeartbeatResult)

  case class ElectionCompleted(result: ElectionResult)

  case class Election()

  case class OwnHeartbeat()

  case class LeaderHeartbeat()

  case class ElectionTick()

  case class OwnHeartbeatTick()

  case class LeaderHeartbeatTick()

}

class RaftActor(val state: NodeState) extends Actor with Timers with RaftFollowerActor with RaftLeaderActor {

  val electionTimer = (scala.util.Random.nextInt(5000) + 5000).milliseconds
  val heartBeatTimer = 500.milliseconds

  lazy val logger: Logger = initLogger

  import RaftActorMessages._

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  timers.startTimerAtFixedRate(ElectionTick, Election, electionTimer)
  logger.info(s"Timer for election started, $electionTimer ms")
  timers.startTimerAtFixedRate(OwnHeartbeatTick, OwnHeartbeat, heartBeatTimer)
  logger.info(s"Timer for heartbeat started, $heartBeatTimer ms")

  def becomeFollower(term: Option[Int], leader: Int): Unit = {
    state.mode = Follower
    state.votedFor = None
    state.lastMessageFromLeader = Some(DateTime.now)
    state.leader = Some(leader)

    logger.info(s"Node ${state.id} became Follower.")
    timers.cancel(LeaderHeartbeatTick)

    term match {
      case Some(term) => state.currentTerm = term
      case _ => None
    }

    context.unbecome()
  }

  def mergeEntries(prevLogTerm: Int, prevLogIndex: Int, entries: Seq[LogEntry]): Seq[LogEntry] = {
    val (prevLog, postLog) = state.log.span(e => e.term <= prevLogTerm && e.index <= prevLogIndex)
    val keep = postLog.zipAll(entries, null, null).takeWhile {
      case (_, null) => true
      case (null, _) => false
      case (existing, update) => existing.term == update.term && existing.index == update.index
    }.map {
      case (existing, _) => existing
    }
    prevLog ++ keep ++ entries.drop(keep.length)
  }


  def requestVote(voteRequest: RequestVoteRequest): RequestVoteResponse = {
    logger.info(voteRequest)
    val myOldTerm = state.currentTerm
    if (voteRequest.serverTerm > state.currentTerm) {
      becomeFollower(Some(voteRequest.serverTerm), voteRequest.serverId)
    }
    logger.info(s"My term is $myOldTerm, term in request is ${voteRequest.serverTerm}")
    val resp = if (voteRequest.serverTerm < myOldTerm) {
      RequestVoteResponse(myOldTerm, voteGranted = false)
    } else {
      logger.info(state)
      if ((state.votedFor.isEmpty || state.votedFor.contains(voteRequest.serverId)) && (voteRequest.lastLogTerm >= state.lastLogTerm
        //|| (voteRequest.lastLogTerm == state.lastLogTerm && voteRequest.lastLogIndex >= state.lastLogIndex)
        )) {
        RequestVoteResponse(state.currentTerm, voteGranted = true)
      } else {
        RequestVoteResponse(state.currentTerm, voteGranted = false)
      }
    }
    logger.info(s"Response is $resp")
    resp
  }

  def appendEntries(appendRequest: AppendEntriesRequest): AppendEntriesResponse = {
    if (appendRequest.term > state.currentTerm) {
      becomeFollower(Some(appendRequest.term), appendRequest.leaderId)
    }

    val prevLog = state.log.find(e => e.term == appendRequest.prevLogTerm && e.index == appendRequest.prevLogIndex)

    if (appendRequest.term < state.currentTerm || (appendRequest.prevLogIndex > 0 && prevLog.isEmpty)) {
      AppendEntriesResponse(state.currentTerm, success = false)
    } else {
      state.log = mergeEntries(appendRequest.prevLogTerm, appendRequest.prevLogIndex, appendRequest.entries)

      if (appendRequest.leaderCommit > state.commitIndex) {
        state.commitIndex = Array(appendRequest.leaderCommit, state.lastLogIndex).min
      }
      AppendEntriesResponse(state.currentTerm, success = true)
    }
  }

  private def leaderHeartbeat(): LeaderHeartbeatResponse = if (state.mode != Leader) {
    this.state.lastMessageFromLeader = Some(DateTime.now)
    LeaderHeartbeatResponse()
  } else {
    state.otherNodes.map(conn => {
      conn.connection.leaderHeartbeat(LeaderHeartbeatRequest())
    })
    LeaderHeartbeatResponse()
  }


  override def receive: Receive = {
    case RequestVote(request) => sender() ! requestVote(request)
    case AppendLog(request) => sender() ! appendEntries(request)
    case ElectionCompleted(result) => electionCompleted(result)
    case Election => startElection()
    case OwnHeartbeat => followerHeartbeat()
    case LeaderHeartbeat => sender() ! leaderHeartbeat()
    case a@_ => logger.info(s"Didn't recognize message $a")
  }

  private def initLogger = {
    val log = Logger(s"Raft-actor-${state.id}")
    log.setFormatter(new LogFormatterColored(state.id))
    log.info(s"Start logging on $state")
    log
  }
}
