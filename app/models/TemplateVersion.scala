package models

import java.time.Instant

import aws.Interpreter.ErrorsOr
import cats.data.NonEmptyList
import com.ovoenergy.comms.model._
import com.ovoenergy.comms.templates.model.Brand

import scala.util.control.NonFatal

case class TemplateVersion(templateId: String,
                           version: String,
                           commName: String,
                           commType: CommType,
                           publishedAt: Instant,
                           publishedBy: String,
                           channels: Option[List[Channel]])

object TemplateVersion {
  def apply(templateManifest: TemplateManifest,
            commName: String,
            commType: CommType,
            publishedBy: String,
            channels: List[Channel]): TemplateVersion = {
    TemplateVersion(
      templateManifest.id,
      templateManifest.version,
      commName,
      commType,
      Instant.now(),
      publishedBy,
      Some(channels)
    )
  }
}

case class ZippedRawTemplate(templateFiles: Array[Byte])

//case class TemplateSummary(templateId: String,
//                           commName: String,
//                           commType: CommType,
//                           brand: Brand,
//                           latestVersion: String)

object TemplateSummaryOps {

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
