package com.yermilov

import akka.actor.{Actor, Timers}
import com.yermilov.raft.raft.{AppendEntriesRequest, AppendEntriesResponse, LogEntry, RequestVoteRequest, RequestVoteResponse}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object RaftActorMessages {

  case class AppendLog(request: AppendEntriesRequest)

  case class RequestVote(requestVote: RequestVoteRequest)

  case class HeartbeatSuccess()

  case class ElectionCompleted()

  case class Election()

  case class Heartbeat()

  case class ElectionTick()

  case class HeartbeatTick()

}

class RaftActor(val state: NodeState) extends Actor with Timers {

  import RaftActorMessages._

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  timers.startTimerAtFixedRate(ElectionTick, Election, (scala.util.Random.nextInt(500) + 500) milliseconds)
  timers.startTimerAtFixedRate(HeartbeatTick, Heartbeat, 100 milliseconds)

  def becomeFollower(term: Option[Int]) = ???

  def mergeEntries(prevLogTerm: Int, prevLogIndex: Int, entries: Seq[LogEntry]): Seq[LogEntry] = ???

  def requestVote(voteRequest: RequestVoteRequest): RequestVoteResponse = {
    if (voteRequest.serverTerm > state.currentTerm) {
      becomeFollower(Some(voteRequest.serverTerm))
    }
    if (voteRequest.serverTerm < state.currentTerm) {
      RequestVoteResponse(state.currentTerm, voteGranted = false)
    } else {
      if ((state.votedFor.isEmpty || state.votedFor.contains(voteRequest.serverId)) && (voteRequest.lastLogTerm >= state.lastLogTerm
        //|| (voteRequest.lastLogTerm == state.lastLogTerm && voteRequest.lastLogIndex >= state.lastLogIndex)
        )) {
        RequestVoteResponse(state.currentTerm, voteGranted = true)
      } else {
        RequestVoteResponse(state.currentTerm, voteGranted = false)
      }
    }
  }

  def appendEntries(appendRequest: AppendEntriesRequest): AppendEntriesResponse = {
    if (appendRequest.term > state.currentTerm) {
      becomeFollower(Some(appendRequest.term))
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

  override def receive: Receive = {
    case RequestVote(request) => sender() ! requestVote(request)
    case AppendLog(request) => sender() ! appendEntries(request)
  }
}
