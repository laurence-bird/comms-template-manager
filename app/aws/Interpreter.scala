package aws

import aws.s3.S3Operations
import cats.~>
import com.ovoenergy.comms.model.CommManifest
import logic.{CompressTemplates, RetrieveTemplateFromS3, RetrieveTemplateVersionFromDynamo, TemplateOpA}

object Interpreter {

  type ErrorsOr[A] = Either[String, A]

  def build(context: aws.Context) =
  new (TemplateOpA ~> ErrorsOr){
    override def apply[A](fa: TemplateOpA[A]): ErrorsOr[A] = {
      fa match {
        case RetrieveTemplateFromS3(commManifest: CommManifest) => S3Operations.downloadTemplateFiles(context.s3ClientWrapper, commManifest, context.s3BucketName)

        case RetrieveTemplateVersionFromDynamo(commManifest) =>
          context.dynamo.getTemplateVersion(commManifest.name, commManifest.version).map(r => Right(r)).getOrElse(Left(s"Template ${commManifest.name} version ${commManifest.version} does not exist"))

        case CompressTemplates(templateFiles) => S3Operations.compressFiles(templateFiles)
      }
    }
  }
}
