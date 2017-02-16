package aws

import aws.s3.S3Operations
import cats.~>
import com.ovoenergy.comms.model.CommManifest
import logic._
import templates.TemplateValidator

object Interpreter {

  type ErrorsOr[A] = Either[String, A]

  def build(context: aws.Context) =
  new (TemplateOpA ~> ErrorsOr){
    override def apply[A](fa: TemplateOpA[A]): ErrorsOr[A] = {
      fa match {
        case RetrieveTemplateFromS3(commManifest: CommManifest) => S3Operations.downloadTemplateFiles(context.s3ClientWrapper, commManifest, context.s3TemplatesBucket)

        case RetrieveTemplateVersionFromDynamo(commManifest) =>
          context.dynamo.getTemplateVersion(commManifest.name, commManifest.version).map(r => Right(r)).getOrElse(Left(s"Template ${commManifest.name} version ${commManifest.version} does not exist"))

        case CompressTemplates(templateFiles) => S3Operations.compressFiles(templateFiles)

        case ListTemplateSummaries() => {
          val templateSummaries = context.dynamo.listTemplateSummaries
          if(templateSummaries.isEmpty)
            Left("Failed to find any templates")
          else
            Right(templateSummaries)
        }

        case UploadTemplate(s3File) =>
          context.s3ClientWrapper.uploadFile(s3File)

        case ValidateTemplate(commManifest, templateFilePaths) =>
          TemplateValidator.validateTemplateFileStructure(context.templatesS3ClientWrapper, commManifest, templateFilePaths)

        case RetrieveAllTemplateVersions(commName: String) => {
          val versions = context.dynamo.listVersions(commName)
          if(versions.isEmpty)
            Left(s"Failed to find any templates for comm $commName")
          else
            Right(versions)
        }
      }
    }
  }
}
