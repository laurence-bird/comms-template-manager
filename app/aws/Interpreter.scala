package aws

import java.nio.file.Files

import aws.s3.{S3FileDetails, S3Operations}
import cats.data.NonEmptyList
import cats.{Id, ~>}
import com.ovoenergy.comms.model._
import templates.{UploadedTemplateFile => UploadedTemplate}
import com.ovoenergy.comms.templates.model.template.processed.CommTemplate
import logic._
import models.{TemplateSummaryLegacy, TemplateVersionLegacy}
import pagerduty.PagerDutyAlerter
import templates.{AssetProcessing, Injector}
import templates.validation.{PrintTemplateValidation, TemplateValidator}
import com.ovoenergy.comms.templates
import com.ovoenergy.comms.templates.TemplatesRepo
import play.api.Logger

import scala.util.Right

object Interpreter {

  type ErrorsOr[A] = Either[NonEmptyList[String], A]

  def build(awsContext: aws.Context, pagerDutyContext: PagerDutyAlerter.Context): ~>[TemplateOpA, ErrorsOr] =
    new (TemplateOpA ~> ErrorsOr) {
      override def apply[A](fa: TemplateOpA[A]): ErrorsOr[A] = {
        fa match {
          case RetrieveTemplateFromS3(commManifest: CommManifest) =>
            S3Operations.downloadTemplateFiles(awsContext.s3ClientWrapper,
                                               commManifest,
                                               awsContext.s3RawTemplatesBucket) match {
              case Left(error)    => Left(NonEmptyList.of(error))
              case Right(success) => Right(success)
            }

          case RetrieveTemplateVersionFromDynamo(commManifest) =>
            awsContext.dynamo
              .getTemplateVersion(commManifest.name, commManifest.version)
              .map(r => Right(r))
              .getOrElse(Left(s"Template ${commManifest.name} version ${commManifest.version} does not exist")) match {
              case Left(error)    => Left(NonEmptyList.of(error))
              case Right(success) => Right(success)
            }

          case CompressTemplates(templateFiles) => S3Operations.compressFiles(templateFiles)

          case ListTemplateSummaries => {
            val templateSummaries = awsContext.dynamo.listTemplateSummaries
            if (templateSummaries.isEmpty)
              Left(NonEmptyList.of("Failed to find any templates"))
            else
              Right(templateSummaries)
          }

          case UploadRawTemplateFileToS3(commManifest, uploadedFile, publishedBy) =>
            val key =
              s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}/${uploadedFile.path}"
            val s3File =
              S3FileDetails(uploadedFile.byteArrayContents,
                            key,
                            awsContext.s3RawTemplatesBucket,
                            uploadedFile.contentType)
            awsContext.s3ClientWrapper.uploadFile(s3File) match {
              case Left(error) => {
                PagerDutyAlerter(s"Attempt to publish file by $publishedBy to $key failed", pagerDutyContext)
                Left(NonEmptyList.of(error))
              }
              case Right(success) => Right(success)
            }

          case UploadTemplateAssetFileToS3(commManifest, uploadedFile: UploadedTemplate, publishedBy) =>
            val key =
              s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}/${uploadedFile.path}"
            val s3File =
              S3FileDetails(uploadedFile.byteArrayContents,
                            key,
                            awsContext.s3TemplateAssetsBucket,
                            uploadedFile.contentType)
            awsContext.s3ClientWrapper.uploadFile(s3File) match {
              case Left(error) => {
                Logger.warn(s"Attempt to publish file by $publishedBy to $key failed: $error")
                PagerDutyAlerter(s"Attempt to publish file by $publishedBy to $key failed", pagerDutyContext)
                Left(NonEmptyList.of(error))
              }
              case Right(success) => Right(success)
            }

          case InjectChannelSpecificScript(processedFiles) => {
            Injector.injectIntoTemplate(awsContext, processedFiles)
          }

          case UploadProcessedTemplateFileToS3(commManifest, uploadedFile, publishedBy) =>
            val key =
              s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}/${uploadedFile.path}"
            val s3File =
              S3FileDetails(uploadedFile.byteArrayContents, key, awsContext.s3TemplateFilesBucket)
            awsContext.s3ClientWrapper.uploadFile(s3File) match {
              case Left(error) => {
                PagerDutyAlerter(s"Attempt to publish file by $publishedBy to $key failed", pagerDutyContext)
                Left(NonEmptyList.of(error))
              }
              case Right(success) => Right(success)
            }

          case ProcessTemplateAssets(commManifest, uploadedFiles) =>
            AssetProcessing.processAssets(awsContext.region,
                                          awsContext.s3TemplateAssetsBucket,
                                          commManifest,
                                          uploadedFiles)

          case ValidateTemplate(commManifest, uploadedFiles) =>
            TemplateValidator.validateTemplate(PrintTemplateValidation.validatePrintFiles)(
              awsContext.templatesS3ClientWrapper,
              commManifest,
              uploadedFiles)

          case ValidateTemplateDoesNotExist(commManifest) =>
            if (awsContext.dynamo.listVersions(commManifest.name).isEmpty) {
              Right(())
            } else {
              Left(NonEmptyList.of(s"A template called ${commManifest.name} already exists"))
            }

          case RetrieveAllTemplateVersions(commName: String) => {
            val versions = awsContext.dynamo.listVersions(commName)
            if (versions.isEmpty)
              Left(NonEmptyList.of(s"Failed to find any templates for comm $commName"))
            else
              Right(versions)
          }

          case UploadTemplateToDynamo(commMannifest, publishedBy, channels) =>
            awsContext.dynamo.writeNewVersion(commMannifest, publishedBy, channels) match {
              case Right(()) => Right(())
              case Left(error) => {
                PagerDutyAlerter(
                  s"Attempt to publish template to dynamo by $publishedBy for ${commMannifest.name}, ${commMannifest.version} failed",
                  pagerDutyContext)
                Left(NonEmptyList.of(error))
              }
            }

          case GetNextTemplateSummary(commName) =>
            val latestVersion: ErrorsOr[TemplateSummaryLegacy] =
              awsContext.dynamo.getTemplateSummary(commName).toRight(NonEmptyList.of("No template found"))
            for {
              latestTemplate <- latestVersion.right
              nextVersion    <- TemplateSummaryLegacy.nextVersion(latestTemplate.latestVersion).right
            } yield latestTemplate.copy(latestVersion = nextVersion)

          case GetChannels(commManifest, templateContext) =>
            TemplatesRepo
              .getTemplate(templateContext, commManifest)
              .toEither
              .map((t: CommTemplate[Id]) => {
                val email = t.email.fold[Option[Channel]](None)(_ => Some(Email))
                val sms   = t.sms.fold[Option[Channel]](None)(_ => Some(SMS))
                val print = t.print.fold[Option[Channel]](None)(_ => Some(Print))
                List(email, sms, print).flatten
              })
        }
      }
    }
}
