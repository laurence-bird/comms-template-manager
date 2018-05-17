package templates.validation

import java.nio.charset.StandardCharsets

import aws.Interpreter.ErrorsOr
import cats.Apply
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._
import com.ovoenergy.comms.model.TemplateManifest
import com.ovoenergy.comms.templates.cache.CachingStrategy
import com.ovoenergy.comms.templates.parsing.handlebars.HandlebarsParsing
import com.ovoenergy.comms.templates.retriever.PartialsS3Retriever
import com.ovoenergy.comms.templates.s3.S3Client
import com.ovoenergy.comms.templates.{TemplatesContext, TemplatesRepo}
import templates.{TemplateBuilder, TemplateErrors, UploadedFile, UploadedTemplateFile}

object TemplateValidator {

  private val assetTemplateReferenceRegex = "(?:'|\")(?: *)(assets/[^(\"')]+)(?: *)(?:'|\")".r
  def validateTemplate(
      channelSpecificValidator: List[UploadedTemplateFile] => TemplateErrors[List[UploadedTemplateFile]])(
      s3Client: S3Client,
      templateManifest: TemplateManifest,
      uploadedFiles: List[UploadedFile]): ErrorsOr[List[UploadedTemplateFile]] = {

    val fileValidations            = validateIfAllFilesAreExpected(uploadedFiles).andThen(channelSpecificValidator)
    val templateContentValidations = validateTemplateContents(s3Client, templateManifest, uploadedFiles)
    val assetReferenceValidations  = validateAssetsExist(uploadedFiles)
    Apply[TemplateErrors]
      .map3(fileValidations, templateContentValidations, assetReferenceValidations) {
        case (files, _, _) => files
      }
      .toEither
  }

  private def validateIfAllFilesAreExpected(
      uploadedFiles: List[UploadedFile]): TemplateErrors[List[UploadedTemplateFile]] = {
    val expectedFiles = UploadedFile.extractAllExpectedFiles(uploadedFiles)
    val errors =
      uploadedFiles
        .map(_.path)
        .filterNot(expectedFiles.map(_.path).toSet)
        .map(path => s"$path is not an expected template file")
    NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(expectedFiles))
  }

  private def validateTemplateContents(s3Client: S3Client,
                                       templateManifest: TemplateManifest,
                                       uploadedFiles: List[UploadedFile]): TemplateErrors[Unit] = {
    val uploadedTemplateFiles = UploadedFile
      .extractNonAssetFiles(uploadedFiles)

    val templateContext = TemplatesContext(
      templatesRetriever = new TemplateBuilder(uploadedTemplateFiles),
      parser = new HandlebarsParsing(new PartialsS3Retriever(s3Client)),
      cachingStrategy = CachingStrategy.noCache
    )

    val validationResult = for {
      template <- TemplatesRepo.getTemplate(templateContext, templateManifest).toEither.right
      result   <- template.requiredData.toEither.right
    } yield result

    validationResult match {
      case Right(_)    => Valid(())
      case Left(error) => Invalid(error)
    }
  }

  private def validateAssetsExist(uploadedFiles: List[UploadedFile]): TemplateErrors[Unit] = {
    val uploadedAssetFilePaths = UploadedFile
      .extractAssetFiles(uploadedFiles)
      .map(_.path.replaceFirst("^[a-zA-Z]+/", ""))

    val errors = UploadedFile
      .extractNonAssetFiles(uploadedFiles)
      .foldLeft(List[String]())((errors, templateFile) => {
        val fileContents    = templateFile.utf8Content
        val assetReferences = assetTemplateReferenceRegex.findAllMatchIn(fileContents)
        val uploadedFileErrors = assetReferences.toList
          .map(_.group(1))
          .flatMap(assetReference =>
            if (!uploadedAssetFilePaths.contains(assetReference))
              Some(
                s"The file ${templateFile.path} contains the reference '$assetReference' to a non-existent asset file")
            else None)
        uploadedFileErrors ++ errors
      })
    NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(()))
  }
}
