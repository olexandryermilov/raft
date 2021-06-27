package com.yermilov

import com.yermilov.raft.raft.{LogEntry, RaftGrpc}
import org.joda.time.DateTime

import scala.collection.mutable

case class NodeState(
                      id: NodeId,
                      otherNodes: Seq[Node],
                      var mode: NodeMode,
                      var currentTerm: Int = 0,
                      var votedFor: Option[NodeId] = None,
                      var leader: Option[NodeId] = None,
                      var lastMessageFromLeader: Option[DateTime] = None,
                      var log: Seq[LogEntry] = Seq(LogEntry("empty log", 0, 0)),
                      var commitIndex: Int = 0,
                      var lastApplied: Int = 0,
                      var nextIndex: mutable.Map[Int, Int] = mutable.Map.empty,
                      var matchIndex: mutable.Map[Int, Int] = mutable.Map.empty,
                    ) {
  def lastLogIndex: Int = if (log.nonEmpty) log.last.index else 0
  def lastLogTerm: Int = if (log.nonEmpty) log.last.term else 0
}

case class Node(connection: RaftGrpc.RaftStub, id: Int)
