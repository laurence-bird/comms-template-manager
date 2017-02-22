package aws

import aws.s3.{S3FileDetails, S3Operations}
import cats.data.NonEmptyList
import cats.~>
import com.ovoenergy.comms.model.CommManifest
import logic._
import templates.TemplateValidator

import scala.util.Right

object Interpreter {

  type ErrorsOr[A] = Either[NonEmptyList[String], A]

  def build(context: aws.Context) =
  new (TemplateOpA ~> ErrorsOr){
    override def apply[A](fa: TemplateOpA[A]): ErrorsOr[A] = {
      fa match {
        case RetrieveTemplateFromS3(commManifest: CommManifest) => S3Operations.downloadTemplateFiles(context.s3ClientWrapper, commManifest, context.s3TemplatesBucket) match {
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

        case UploadTemplateFileToS3Raw(commManifest, uploadedFile) =>
          val key = s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}/${uploadedFile.path}"
          val s3File = S3FileDetails(uploadedFile.contents, key, context.s3TemplatesBucket)
          context.s3ClientWrapper.uploadFile(s3File) match {
            case Left(error) => Left(NonEmptyList.of(error))
            case Right(success) => Right(success)
          }

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
      }
    }
  }
}
