package views

import java.time.{Instant, LocalDate}

object ViewHelper {

  def formatDate(instant: Instant): String =
    instant.toString

  def formatOptDate(opt: Option[Instant]): String =
    opt.map(formatDate).getOrElse("(unknown)")

  def dateToLong(instant: Instant): Long = {
    instant.toEpochMilli
  }

  def commPerformanceLink(commName: String, commPerformanceUrl: String): String = {
    val start = LocalDate.now().minusDays(1).toString
    s"$commPerformanceUrl?commName=$commName&timePeriod=Day&start=$start"
  }

  def commSearchLink(commName: String, commSearchUrl: String): String =
    s"$commSearchUrl?commName=$commName"

}
