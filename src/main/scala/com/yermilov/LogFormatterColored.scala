package com.yermilov

import wvlet.log.{LogFormatter, LogRecord, LogTimestampFormatter}
import wvlet.log.LogFormatter.{appendStackTrace, withColor}

class LogFormatterColored(id: Int) extends LogFormatter {
  val colors = Array(
    Console.BLUE,
    Console.YELLOW,
    Console.RED,
    Console.GREEN,
    Console.WHITE,
    Console.RESET
  )

  override def formatLog(r: LogRecord): String = {
    val color = colors((id-1) % colors.length)

    val log = s"[${LogTimestampFormatter.formatTimestampWithNoSpaace(r.getMillis)}][${withColor(color, r.leafLoggerName)}] ${withColor(color, r.getMessage)}"
    appendStackTrace(log, r)
  }
}
