package com.yermilov

sealed trait NodeMode

case object Follower extends NodeMode
case object Leader extends NodeMode
case object Candidate extends NodeMode
