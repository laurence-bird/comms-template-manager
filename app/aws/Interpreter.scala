package aws

import aws.s3.{S3FileDetails, S3Operations}
import cats.data.NonEmptyList
import cats.~>
import com.ovoenergy.comms.model.{CommManifest, CommType}
import logic._
import models.{TemplateSummary, TemplateVersion}
import templates.{AssetProcessing, TemplateValidator}

import scala.util.Right

object Interpreter {

  type ErrorsOr[A] = Either[NonEmptyList[String], A]

  def build(context: aws.Context) =
  new (TemplateOpA ~> ErrorsOr){
    override def apply[A](fa: TemplateOpA[A]): ErrorsOr[A] = {
      fa match {
        case RetrieveTemplateFromS3(commManifest: CommManifest) => S3Operations.downloadTemplateFiles(context.s3ClientWrapper, commManifest, context.s3RawTemplatesBucket) match {
          case Left(error)    => Left(NonEmptyList.of(error))
          case Right(success) => Right(success)
        }

        case RetrieveTemplateVersionFromDynamo(commManifest) =>
          context.dynamo.getTemplateVersion(commManifest.name, commManifest.version).map(r => Right(r)).getOrElse(Left(s"Template ${commManifest.name} version ${commManifest.version} does not exist")) match {
            case Left(error)    => Left(NonEmptyList.of(error))
            case Right(success) => Right(success)
          }

        case CompressTemplates(templateFiles) => S3Operations.compressFiles(templateFiles)

        case ListTemplateSummaries() => {
          val templateSummaries = context.dynamo.listTemplateSummaries
          if(templateSummaries.isEmpty)
            Left(NonEmptyList.of("Failed to find any templates"))
          else
            Right(templateSummaries)
        }

        case UploadRawTemplateFileToS3(commManifest, uploadedFile) =>
          val key = s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}/${uploadedFile.path}"
          val s3File = S3FileDetails(uploadedFile.contents, key, context.s3RawTemplatesBucket)
          context.s3ClientWrapper.uploadFile(s3File) match {
            case Left(error)    => Left(NonEmptyList.of(error))
            case Right(success) => Right(success)
          }

        case UploadTemplateAssetFileToS3(commManifest, uploadedFile) =>
          val key = s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}/${uploadedFile.path}"
          val s3File = S3FileDetails(uploadedFile.contents, key, context.s3TemplateAssetsBucket)
          context.s3ClientWrapper.uploadFile(s3File) match {
            case Left(error)    => Left(NonEmptyList.of(error))
            case Right(success) => Right(success)
          }

        case UploadProcessedTemplateFileToS3(commManifest, uploadedFile) =>
          val key = s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}/${uploadedFile.path}"
          val s3File = S3FileDetails(uploadedFile.contents, key, context.s3TemplateFilesBucket)
          context.s3ClientWrapper.uploadFile(s3File) match {
            case Left(error)    => Left(NonEmptyList.of(error))
            case Right(success) => Right(success)
          }

        case ProcessTemplateAssets(commManifest, uploadedFiles) =>
          AssetProcessing.processAssets(context.region, context.s3TemplateAssetsBucket, commManifest, uploadedFiles)

        case ValidateTemplate(commManifest, uploadedFiles) =>
          TemplateValidator.validateTemplate(context.templatesS3ClientWrapper, commManifest, uploadedFiles)

        case ValidateTemplateDoesNotExist(commManifest) =>
          if (context.dynamo.listVersions(commManifest.name).isEmpty) {
            Right(())
          } else {
            Left(NonEmptyList.of(s"A template called ${commManifest.name} already exists"))
          }

        case RetrieveAllTemplateVersions(commName: String) => {
          val versions = context.dynamo.listVersions(commName)
          if(versions.isEmpty)
            Left(NonEmptyList.of(s"Failed to find any templates for comm $commName"))
          else
            Right(versions)
        }

        case UploadTemplateToDynamo(commMannifest) =>
          context.dynamo.writeNewVersion(commMannifest) match {
            case Right(())   => Right(())
            case Left(error) => Left(NonEmptyList.of(error))
          }

        case GetNextTemplateVersion(commName, commType) =>
          val latestVersion: ErrorsOr[String] = context.dynamo.latestVersion(commName, CommType.CommTypeFromValue(commType)).toRight(NonEmptyList.of("No template found"))
          for {
            tVersion    <- latestVersion.right
            nextVersion <- TemplateSummary.nextVersion(tVersion).right
          } yield nextVersion

      }
    }
  }
}
