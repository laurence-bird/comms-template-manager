package models

import java.time.Instant

import com.ovoenergy.comms.model.{CommManifest, CommType}


case class TemplateVersion(commName: String, version: String, publishedAt: Instant, publishedBy: String, commType: CommType)

object TemplateVersion{
  def apply(commManifest: CommManifest, publishedBy: String): TemplateVersion = {
    TemplateVersion(
      commManifest.name,
      commManifest.version,
      Instant.now(),
      publishedBy,
      commManifest.commType
    )
  }
}

case class ZippedRawTemplate(templateFiles: Array[Byte])

case class TemplateSummary(commName: String, commType: CommType, latestVersion: String)

object TemplateSummary{
  def apply(commManifest: CommManifest): TemplateSummary = {
    TemplateSummary(
      commManifest.name,
      commManifest.commType,
      commManifest.version
    )
  }

  def versionCompare(l: String, r: String): Int = {
    val leftVersionVals = l.split("\\.").map(_.toInt)
    val rightVersionVals = r.split("\\.").map(_.toInt)

    val positionOfDifference = leftVersionVals.foldLeft(0)((position, vals1Value) => {
      if (position < rightVersionVals.length && vals1Value.equals(rightVersionVals(position))) position + 1
      else position
    })

    // compare first non-equal ordinal number
    if (positionOfDifference < leftVersionVals.length && positionOfDifference < rightVersionVals.length) {
      val diff = leftVersionVals(positionOfDifference).compareTo(rightVersionVals(positionOfDifference))
      Integer.signum(diff)
    } else {
      // the strings are equal or one string is a substring of the other
      // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
      Integer.signum(leftVersionVals.length - rightVersionVals.length)
    }
  }
}


