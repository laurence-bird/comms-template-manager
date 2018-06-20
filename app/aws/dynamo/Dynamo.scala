package aws.dynamo

import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputExceededException
import com.gu.scanamo._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.error.DynamoReadError._
import com.gu.scanamo.syntax._
import com.ovoenergy.comms.model.{Channel, CommType, TemplateManifest}
import com.ovoenergy.comms.templates.{
  ErrorsOr,
  TemplateMetadataContext,
  TemplateMetadataDynamoFormats,
  TemplateMetadataRepo
}
import com.ovoenergy.comms.templates.model.Brand
import com.ovoenergy.comms.templates.model.template.metadata.{TemplateId, TemplateSummary}
import components.Retry
import components.Retry.{Failed, RetryConfig, Succeeded}
import models.{TemplateSummaryOps, TemplateVersion}
import play.api.Logger

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

case class TemplateSummaryTable(templateId: String,
                                commName: String,
                                commType: CommType,
                                brand: Brand,
                                latestVersion: String) {
  def toTemplateSummary = TemplateSummary(
    TemplateId(templateId),
    commName,
    commType,
    brand,
    latestVersion
  )
}

object TemplateSummaryTable {
  def apply(templateSummary: TemplateSummary): TemplateSummaryTable = {
    TemplateSummaryTable(
      templateSummary.templateId.value,
      templateSummary.commName,
      templateSummary.commType,
      templateSummary.brand,
      templateSummary.latestVersion
    )
  }
}

class Dynamo(db: AmazonDynamoDBAsync, templateVersionTableName: String, templateSummaryTableName: String)
    extends DynamoFormats {

  val templateVersionTable    = Table[TemplateVersion](templateVersionTableName)
  val templateSummaryTable    = Table[TemplateSummary](templateSummaryTableName)
  val templateMetadataContext = TemplateMetadataContext(db, templateSummaryTableName)

  def listVersions(templateId: String): Seq[TemplateVersion] = {
    val query = templateVersionTable.query('templateId -> templateId)
    Scanamo.exec(db)(query).flatMap { result =>
      logIfError(result).toOption
    }
  }

  //TODO - Not a conditional write as desiredn
  def writeNewVersion(templateManifest: TemplateManifest,
                      commName: String,
                      commType: CommType,
                      brand: Brand,
                      publishedBy: String,
                      channels: List[Channel]): Either[String, Unit] = {

    if (isNewestVersion(templateManifest)) {

      val templateVersion = TemplateVersion(
        templateManifest,
        commName,
        commType,
        publishedBy,
        channels
      )

      Scanamo.exec(db)(templateVersionTable.put(templateVersion))
      Logger.info(s"Written template version to persistence $templateVersion")

      val templateSummary = TemplateSummary(
        TemplateId(templateManifest.id),
        commName,
        commType,
        brand,
        templateManifest.version
      )

      implicit val fmt = templateIdFormat

      Scanamo.exec(db)(templateSummaryTable.put(templateSummary))
      Logger.info(s"Written template summary to persistence $templateSummary")

      Right(())
    } else {
      getTemplateSummary(templateManifest.id)
        .map(_.latestVersion)
        .fold(
          error => Left(error.toString()),
          version =>
            Left(
              s"There is a newer version ($version) of comm (${templateManifest.id}) already, than being published (${templateManifest.version})")
        )
    }
  }

  def listTemplateSummaries: List[TemplateSummary] = {
    val query = templateSummaryTable.scan()
    Scanamo
      .exec(db)(query)
      .flatMap { result =>
        logIfError(result).toOption
      }
  }

  val retryConfig: RetryConfig = RetryConfig(5, Retry.constantDelayFunc(FiniteDuration(1, TimeUnit.SECONDS)))

  val retry = Retry.retry[NonEmptyList[String], TemplateSummary](
    retryConfig,
    (error: NonEmptyList[String]) => Logger.warn(error.head),
    _ => true
  ) _

  def getTemplateSummary(templateId: String) =
    retry {
      Try {
        TemplateMetadataRepo.getTemplateSummary(templateMetadataContext, TemplateId(templateId)) match {
          case None                               => Left(NonEmptyList.of(s"TemplateSummary for templateId $templateId has not been found"))
          case Some(s: ErrorsOr[TemplateSummary]) => s.toEither
        }
      } match {
        case Success(s) => s
        case Failure(f) =>
          Left(NonEmptyList.of("Templates database is temporarily unavailable, please try again in 5 minutes."))
      }
    }.flattenRetry

  // FIXME this does not work in the real life as the version is not indexed.
  def getTemplateVersion(templateId: String, version: String): Option[TemplateVersion] = {
    val query = templateVersionTable.get('templateId -> templateId and 'version -> version)
    Scanamo.exec(db)(query).flatMap(result => logIfError(result).toOption)
  }

  private def logIfError[A](res: Either[DynamoReadError, A]): Either[DynamoReadError, A] = {
    res.left.map { err =>
      Logger.warn(s"Dynamo query failed with error: ${describe(err)}")
      err
    }
  }

  private def isNewestVersion(templateManifest: TemplateManifest): Boolean = {
    getTemplateSummary(templateManifest.id)
      .map(summary => TemplateSummaryOps.versionCompare(templateManifest.version.trim, summary.latestVersion.trim))
      .forall {
        case Right(comparison) => if (comparison > 0) true else false
        case Left(error) =>
          Logger.warn("Unable to check if template is latest version: $error")
          false
      }
  }
}
