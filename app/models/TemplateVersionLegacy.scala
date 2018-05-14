package models

import java.time.Instant

import aws.Interpreter.ErrorsOr
import cats.data.NonEmptyList
import com.ovoenergy.comms.model.{Channel, CommManifest, CommType}

import scala.util.Try
import scala.util.control.NonFatal

case class TemplateVersionLegacy(commName: String,
                                 version: String,
                                 publishedAt: Instant,
                                 publishedBy: String,
                                 commType: CommType,
                                 channels: Option[List[Channel]])

object TemplateVersionLegacy {
  def apply(commManifest: CommManifest, publishedBy: String, channels: List[Channel]): TemplateVersionLegacy = {
    TemplateVersionLegacy(
      commManifest.name,
      commManifest.version,
      Instant.now(),
      publishedBy,
      commManifest.commType,
      Some(channels)
    )
  }
}

//case class ZippedRawTemplate(templateFiles: Array[Byte])

case class TemplateSummaryLegacy(commName: String, commType: CommType, latestVersion: String)

object TemplateSummaryLegacy {
  def apply(commManifest: CommManifest): TemplateSummaryLegacy = {
    TemplateSummaryLegacy(
      commManifest.name,
      commManifest.commType,
      commManifest.version
    )
  }

  def nextVersion(version: String): ErrorsOr[String] = {
    try {
      val versionSeq = version.split("\\.").map(_.toInt)
      versionSeq.headOption match {
        case Some(number) => Right(s"${number + 1}${".0" * (versionSeq.length - 1)}")
        case None         => Left(NonEmptyList.of("No version passed"))
      }
    } catch {
      case NonFatal(e) => Left(NonEmptyList.of(e.getMessage))
    }
  }

  def versionCompare(l: String, r: String): ErrorsOr[Int] = {
    try {
      val leftVersionVals  = l.split("\\.").map(_.toInt)
      val rightVersionVals = r.split("\\.").map(_.toInt)

      //A pragmatic use of return?
      def findDifferencePosition(): Int = {
        leftVersionVals.foldLeft(0)((position, vals1Value) => {
          if (position < rightVersionVals.length && vals1Value.equals(rightVersionVals(position))) position + 1
          else return position
        })
      }

      val positionOfDifference = findDifferencePosition()

      // compare first non-equal ordinal number
      if (positionOfDifference < leftVersionVals.length && positionOfDifference < rightVersionVals.length) {
        val diff = leftVersionVals(positionOfDifference).compareTo(rightVersionVals(positionOfDifference))
        Right(Integer.signum(diff))
      } else {
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        Right(Integer.signum(leftVersionVals.length - rightVersionVals.length))
      }
    } catch {
      case NonFatal(e) => Left(NonEmptyList.of(e.getMessage))
    }
  }
}