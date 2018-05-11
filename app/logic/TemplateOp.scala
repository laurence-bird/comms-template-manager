package logic

import aws.Interpreter.ErrorsOr
import cats.Id
import cats.data.NonEmptyList
import cats.free.Free
import cats.free.Free._
import com.ovoenergy.comms.model.{Channel, CommManifest, CommType}
import com.ovoenergy.comms.templates.model.template.processed.CommTemplate
import com.ovoenergy.comms.templates.{TemplatesContext, TemplatesRepo}
import models.{TemplateSummaryLegacy, TemplateVersionLegacy, ZippedRawTemplate}
import play.api.Logger
import templates.AssetProcessing.ProcessedFiles
import templates.{UploadedFile, UploadedTemplateFile}

object TemplateOp {

  type TemplateOp[A] = Free[TemplateOpA, A]

  type TemplateFiles = Map[String, Array[Byte]]

  def retrieveTemplateFromS3(commManifest: CommManifest): TemplateOp[TemplateFiles] =
    liftF(RetrieveTemplateFromS3(commManifest))

  def retrieveTemplateInfo(commManifest: CommManifest): TemplateOp[TemplateVersionLegacy] =
    liftF(RetrieveTemplateVersionFromDynamo(commManifest))

  def retrieveAllTemplateVersions(commName: String): TemplateOp[Seq[TemplateVersionLegacy]] =
    liftF(RetrieveAllTemplateVersions(commName))

  def compressTemplatesToZipFile(templateFiles: TemplateFiles): TemplateOp[Array[Byte]] =
    liftF(CompressTemplates(templateFiles))

  def listTemplateSummaries(): TemplateOp[Seq[TemplateSummaryLegacy]] =
    liftF(ListTemplateSummaries)

  def validateAndUploadExistingTemplate(commName: String,
                                        uploadedFiles: List[UploadedFile],
                                        publishedBy: String,
                                        context: TemplatesContext): TemplateOp[TemplateSummaryLegacy] = {
    for {
      nextVersion <- getNextTemplateSummary(commName)
      commManifest = CommManifest(nextVersion.commType, commName, nextVersion.latestVersion)
      templateFiles <- validateTemplate(commManifest, uploadedFiles)
      _             <- uploadProcessedTemplateToS3(commManifest, templateFiles, publishedBy)
      _             <- uploadRawTemplateToS3(commManifest, templateFiles, publishedBy)
      templates     <- getChannels(commManifest, context)
      _             <- writeTemplateToDynamo(commManifest, publishedBy, templates)
    } yield nextVersion
  }

  def validateAndUploadNewTemplate(commManifest: CommManifest,
                                   uploadedFiles: List[UploadedFile],
                                   publishedBy: String,
                                   context: TemplatesContext): TemplateOp[List[String]] = {
    for {
      _                      <- validateTemplateDoesNotExist(commManifest)
      templateFiles          <- validateTemplate(commManifest, uploadedFiles)
      processedUploadResults <- uploadProcessedTemplateToS3(commManifest, templateFiles, publishedBy)
      rawUploadResults       <- uploadRawTemplateToS3(commManifest, templateFiles, publishedBy)
      channels               <- getChannels(commManifest, context)
      _                      <- writeTemplateToDynamo(commManifest, publishedBy, channels)
    } yield rawUploadResults ++ processedUploadResults
  }

  def uploadProcessedTemplateToS3(commManifest: CommManifest,
                                  uploadedFiles: List[UploadedTemplateFile],
                                  publishedBy: String): TemplateOp[List[String]] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    for {
      processedFiles <- processTemplateAssets(commManifest, uploadedFiles)
      assetsUploadResults <- processedFiles.assetFiles.traverse(file =>
        uploadTemplateAssetFileToS3(commManifest, file, publishedBy))
      furtherProcessedFiles <- injectChannelSpecificStuff(processedFiles)
      templateFilesUploadResults <- furtherProcessedFiles.templateFiles.traverse(file =>
        uploadProcessedTemplateFileToS3(commManifest, file, publishedBy))
    } yield assetsUploadResults ++ templateFilesUploadResults
  }

  def uploadRawTemplateToS3(commManifest: CommManifest,
                            uploadedFiles: List[UploadedTemplateFile],
                            publishedBy: String): TemplateOp[List[String]] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    uploadedFiles.traverse(file => uploadRawTemplateFileToS3(commManifest, file, publishedBy))
  }

  def getChannels(commManifest: CommManifest, context: TemplatesContext): TemplateOp[List[Channel]] = {
    liftF(GetChannels(commManifest, context))
  }

  def processTemplateAssets(commManifest: CommManifest,
                            uploadedFiles: List[UploadedTemplateFile]): TemplateOp[ProcessedFiles] = {
    liftF(ProcessTemplateAssets(commManifest, uploadedFiles))
  }

  def injectChannelSpecificStuff(processedFiles: ProcessedFiles): TemplateOp[ProcessedFiles] = {
    liftF(InjectChannelSpecificScript(processedFiles))
  }

  def getNextTemplateSummary(commName: String): TemplateOp[TemplateSummaryLegacy] = {
    liftF(GetNextTemplateSummary(commName))
  }

  def writeTemplateToDynamo(commManifest: CommManifest, publishedBy: String, channels: List[Channel]) = {
    liftF(UploadTemplateToDynamo(commManifest, publishedBy, channels))
  }

  def uploadRawTemplateFileToS3(commManifest: CommManifest,
                                uploadedFile: UploadedTemplateFile,
                                publishedBy: String): TemplateOp[String] =
    liftF(UploadRawTemplateFileToS3(commManifest, uploadedFile, publishedBy))

  def uploadProcessedTemplateFileToS3(commManifest: CommManifest,
                                      uploadedFile: UploadedTemplateFile,
                                      publishedBy: String): TemplateOp[String] =
    liftF(UploadProcessedTemplateFileToS3(commManifest, uploadedFile, publishedBy))

  def uploadTemplateAssetFileToS3(commManifest: CommManifest,
                                  uploadedFile: UploadedTemplateFile,
                                  publishedBy: String): TemplateOp[String] =
    liftF(UploadTemplateAssetFileToS3(commManifest, uploadedFile, publishedBy))

  def validateTemplate(commManifest: CommManifest,
                       uploadedFiles: List[UploadedFile]): TemplateOp[List[UploadedTemplateFile]] =
    liftF(ValidateTemplate(commManifest, uploadedFiles))

  def validateTemplateDoesNotExist(commManifest: CommManifest): TemplateOp[Unit] =
    liftF(ValidateTemplateDoesNotExist(commManifest))

  def retrieveTemplate(commManifest: CommManifest): TemplateOp[ZippedRawTemplate] =
    for {
      template   <- retrieveTemplateFromS3(commManifest)
      zippedFile <- compressTemplatesToZipFile(template)
    } yield ZippedRawTemplate(zippedFile)

}
