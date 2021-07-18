#!/bin/bash

set -x

sbt "runMain com.yermilov.RaftServer $ID"