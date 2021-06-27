package com.yermilov

import akka.actor.{Actor, Timers}
import com.yermilov.raft.raft.{AppendEntriesRequest, AppendEntriesResponse, AppendLogToLeaderResponse, GetLeaderResponse, GetNodeStateResponse, LeaderHeartbeatRequest, LeaderHeartbeatResponse, LogEntry, RequestVoteRequest, RequestVoteResponse}
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

  case class GetLeader()

  case class AddLogToLeader(logValue: String)

  case class GetNodeState()

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

  def leaderAppend(valueToAppend: String): AppendLogToLeaderResponse = {
    if(state.mode == Leader) {
      logger.info(s"$valueToAppend came to server ${state.id}")
      appendEntries(AppendEntriesRequest(state.currentTerm, state.id, state.log.length - 1, state.log.lastOption.map(_.term).getOrElse(state.currentTerm), entries = Seq(LogEntry(valueToAppend, state.log.length, state.currentTerm)), state.commitIndex))
    }
    AppendLogToLeaderResponse()
  }

  def appendEntries(appendRequest: AppendEntriesRequest): AppendEntriesResponse = {
    val oldTerm = state.currentTerm
    logger.info(s"Append entries request, current term is $oldTerm")
    if (appendRequest.term > state.currentTerm) {
      becomeFollower(Some(appendRequest.term), appendRequest.leaderId)
    }
    state.lastMessageFromLeader = Some(DateTime.now())

    val prevLog = state.log.find(e => e.term == appendRequest.prevLogTerm && e.index == appendRequest.prevLogIndex)

    if (appendRequest.term < oldTerm || (appendRequest.prevLogIndex > 0 && prevLog.isEmpty)) {
      AppendEntriesResponse(oldTerm, success = false)
    } else {
      logger.info(s"Appends entries come through all ifs")
      state.log = mergeEntries(appendRequest.prevLogTerm, appendRequest.prevLogIndex, appendRequest.entries)
      logger.info(s"New log is ${state.log}")
      if (appendRequest.leaderCommit >= state.commitIndex) {
        state.commitIndex = Array(appendRequest.leaderCommit, state.lastLogIndex).min
        logger.info(s"Updated commit index to ${state.commitIndex}")
      }
      AppendEntriesResponse(state.currentTerm, success = true)
    }
  }

  private def leaderHeartbeat(): LeaderHeartbeatResponse = {
    if (state.mode == Leader) {
      heartbeat()
    } else {
      logger.info("Got heartbeat from leader")
    }
    LeaderHeartbeatResponse()
  }

  /*if (state.mode != Leader) {
    logger.info("Leader heartbeat received")
    this.state.lastMessageFromLeader = Some(DateTime.now)
    LeaderHeartbeatResponse()
  } else {
    /*state.otherNodes.map(conn => {
      conn.connection.leaderHeartbeat(LeaderHeartbeatRequest())
    })
    LeaderHeartbeatResponse()*/
  }*/


  override def receive: Receive = {
    case RequestVote(request) => sender() ! requestVote(request)
    case AppendLog(request) => sender() ! appendEntries(request)
    case ElectionCompleted(result) => electionCompleted(result)
    case Election => startElection()
    case OwnHeartbeat => followerHeartbeat()
    case LeaderHeartbeat => leaderHeartbeat()
    case LeaderHeartbeat() => leaderHeartbeat()
    case HeartbeatSuccess(result) => heartbeatCompleted(result)
    case GetLeader() => sender() ! GetLeaderResponse(state.mode == Leader)
    case GetLeader => sender() ! GetLeaderResponse(state.mode == Leader)
    case GetNodeState => sender() ! GetNodeStateResponse(state.log.tail)
    case GetNodeState() => sender() ! GetNodeStateResponse(state.log.tail)
    case AddLogToLeader(logValue) => sender() ! leaderAppend(logValue)
    case a@_ => logger.info(s"Didn't recognize message $a")
  }

  private def initLogger = {
    val log = Logger(s"Raft-actor-${state.id}")
    log.setFormatter(new LogFormatterColored(state.id))
    log.info(s"Start logging on $state")
    log
  }
}
