package templates

import cats.Apply
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import com.ovoenergy.comms.model.CommManifest
import com.ovoenergy.comms.templates.parsing.handlebars.HandlebarsParsing
import com.ovoenergy.comms.templates.retriever.{PartialsS3Retriever, email}
import com.ovoenergy.comms.templates.{TemplatesContext, TemplatesRepo}
import com.ovoenergy.comms.templates.s3.S3Client

object TemplateValidator {

  private val assetTemplateReferenceRegex = "(?:'|\")(?: *)(assets/[^(\"')]+)(?: *)(?:'|\")".r

  def validateTemplateFileStructure(s3Client: S3Client, commManifest: CommManifest, uploadedFiles: List[UploadedFile]): Either[String, Unit] = {
    val expFileValidations = validateIfAllFilesAreExpected(uploadedFiles.map(_.path))
    val templateContentValidations = validateTemplateContents(s3Client, commManifest, uploadedFiles)
    val assetReferenceValidations = validateAllEmailAssetsExist(uploadedFiles)
    val validation = Apply[TemplateErrors].map3(expFileValidations, templateContentValidations, assetReferenceValidations) {
      case (_, _, _) => ()
    }
    validation match {
      case Valid(_) => Right(())
      case Invalid(errors) => Left(errors.toList.mkString("\n"))
    }
  }

  private def validateIfAllFilesAreExpected(uploadedFiles: List[String]): TemplateErrors[Unit] = {
    val errors = uploadedFiles.foldLeft(List[String]())((e, templateFilePath) => {
      if (TemplateFileRegexes.Email.allRegexes.exists(_.findFirstMatchIn(templateFilePath).isDefined)) {
        e
      } else {
        s"$templateFilePath is not an expected template file" :: e
      }
    })
    NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(()))
  }

  private def validateTemplateContents(s3Client: S3Client, commManifest: CommManifest, uploadedFiles: List[UploadedFile]): TemplateErrors[Unit] = {
    val uploadedTemplateFiles = extractNonAssetEmailTemplateFiles(uploadedFiles)
    val templateContext = TemplatesContext(
      emailTemplateRetriever = new EmailTemplateBuilder(uploadedTemplateFiles),
      parser = new HandlebarsParsing(new PartialsS3Retriever(s3Client))
    )

    val validationResult = for {
      template <- TemplatesRepo.getTemplate(templateContext, commManifest).toEither.right
      result <- template.combineRequiredData.toEither.right
    } yield result

    validationResult match {
      case Right(_)    => Valid(())
      case Left(error) => Invalid(error)
    }
  }

  private def validateAllEmailAssetsExist(uploadedFiles: List[UploadedFile]): TemplateErrors[Unit] = {
    val assetFilePathsRegex = TemplateFileRegexes.Email.assets.regex
    val uploadedAssetFilePaths = uploadedFiles
        .filter(uploadedFile => assetFilePathsRegex.findFirstIn(uploadedFile.path).isDefined)
        .map(uploadedFile => uploadedFile.path.replaceFirst("^email/", ""))

    val errors = extractNonAssetEmailTemplateFiles(uploadedFiles).foldLeft(List[String]())((errors, emailTemplateFile) => {
      val emailTemplateFileContents = new String(emailTemplateFile.contents)
      val emailTemplateFileAssetReferences = assetTemplateReferenceRegex.findAllMatchIn(emailTemplateFileContents)
      val uploadedFileErrors = emailTemplateFileAssetReferences
        .toList
        .map(_.group(1))
        .flatMap(assetReference =>
          if (!uploadedAssetFilePaths.contains(assetReference)) Some(s"The email ${emailTemplateFile.fileType} file contains the reference '$assetReference' to a non-existent asset file")
          else None
        )
      uploadedFileErrors ++ errors
    })
    NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(()))
  }

  private def extractNonAssetEmailTemplateFiles(uploadedFiles: List[UploadedFile]): List[EmailTemplateFile] = {
    TemplateFileRegexes.Email.nonAssetFiles
      .flatMap{ templateFileRegex =>
        uploadedFiles
          .find(uploadedFile => templateFileRegex.regex.findFirstMatchIn(uploadedFile.path).isDefined)
          .map(uploadedFile => EmailTemplateFile(templateFileRegex.fileType, uploadedFile.contents))
      }
  }
}
