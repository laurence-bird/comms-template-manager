package templates

import java.util.concurrent.Future

import aws.Interpreter.ErrorsOr
import cats.Apply
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import com.ovoenergy.comms.model.CommManifest
import com.ovoenergy.comms.templates.cache.CachingStrategy
import com.ovoenergy.comms.templates.model.template.files.sms.SMSTemplateFiles
import com.ovoenergy.comms.templates.parsing.handlebars.HandlebarsParsing
import com.ovoenergy.comms.templates.retriever.PartialsS3Retriever
import com.ovoenergy.comms.templates.{TemplatesContext, TemplatesRepo}
import com.ovoenergy.comms.templates.s3.S3Client

object TemplateValidator {

  private val assetTemplateReferenceRegex = "(?:'|\")(?: *)(assets/[^(\"')]+)(?: *)(?:'|\")".r
  def validateTemplate(s3Client: S3Client,
                       commManifest: CommManifest,
                       uploadedFiles: List[UploadedFile]): ErrorsOr[List[UploadedTemplateFile]] = {

    val expFileValidations         = validateIfAllFilesAreExpected(uploadedFiles)
    val templateContentValidations = validateTemplateContents(s3Client, commManifest, uploadedFiles)
    val assetReferenceValidations  = validateAssetsExist(uploadedFiles)
    val templateErrorsOr = Apply[TemplateErrors]
      .map3(expFileValidations, templateContentValidations, assetReferenceValidations) {
        case (files, _, _) => files
      }
      .toEither

    templateErrorsOr.right.flatMap(validateChannelSpecificRequirements)
  }

  private def validateChannelSpecificRequirements(
      uploadedFiles: List[UploadedTemplateFile]): ErrorsOr[List[UploadedTemplateFile]] = {
    /*
              TODO: Validation for:
          - Address box is present
          - No JS is included
          - Colour encoding is CMYK
          - Images are cmyk
          - Print image assets are only TIFF or JPEG, and are CMYK encoded

     */
    null
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
                                       commManifest: CommManifest,
                                       uploadedFiles: List[UploadedFile]): TemplateErrors[Unit] = {
    val uploadedTemplateFiles = UploadedFile
      .extractNonAssetFiles(uploadedFiles)

    val templateContext = TemplatesContext(
      templatesRetriever = new TemplateBuilder(uploadedTemplateFiles),
      parser = new HandlebarsParsing(new PartialsS3Retriever(s3Client)),
      cachingStrategy = CachingStrategy.noCache
    )

    val validationResult = for {
      template <- TemplatesRepo.getTemplate(templateContext, commManifest).toEither.right
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
        val fileContents    = new String(templateFile.contents)
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
