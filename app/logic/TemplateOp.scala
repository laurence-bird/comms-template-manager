package logic

import cats.free.Free
import cats.free.Free._
import com.ovoenergy.comms.model._
import com.ovoenergy.comms.templates.model.Brand
import com.ovoenergy.comms.templates.model.template.metadata.TemplateSummary
import com.ovoenergy.comms.templates.TemplatesContext
import models.{TemplateVersion, ZippedRawTemplate}
import templates.AssetProcessing.ProcessedFiles
import templates.{UploadedFile, UploadedTemplateFile}

object TemplateOp {

  type TemplateOp[A] = Free[TemplateOpA, A]

  type TemplateFiles = Map[String, Array[Byte]]

  def retrieveTemplateFromS3(templateManifest: TemplateManifest): TemplateOp[TemplateFiles] =
    liftF(RetrieveTemplateFromS3(templateManifest))

  def retrieveTemplateInfo(templateManifest: TemplateManifest): TemplateOp[TemplateVersion] =
    liftF(RetrieveTemplateVersionFromDynamo(templateManifest))

  def retrieveAllTemplateVersions(templateId: String, commName: String): TemplateOp[Seq[TemplateVersion]] =
    liftF(RetrieveAllTemplateVersions(templateId, commName))

  def compressTemplatesToZipFile(templateFiles: TemplateFiles): TemplateOp[Array[Byte]] =
    liftF(CompressTemplates(templateFiles))

  def listTemplateSummaries(): TemplateOp[Seq[TemplateSummary]] =
    liftF(ListTemplateSummaries)

  def validateAndUploadExistingTemplate(templateId: String,
                                        commName: String,
                                        uploadedFiles: List[UploadedFile],
                                        publishedBy: String,
                                        context: TemplatesContext): TemplateOp[TemplateSummary] = {
    for {
      nextVersion <- getNextTemplateSummary(templateId)
      templateManifest = TemplateManifest(templateId, nextVersion.latestVersion)
      templateFiles <- validateTemplate(templateManifest, uploadedFiles)
      _             <- uploadProcessedTemplateToS3(templateManifest, templateFiles, publishedBy)
      _             <- uploadRawTemplateToS3(templateManifest, templateFiles, publishedBy)
      templates     <- getChannels(templateManifest, context)
      _ <- writeTemplateToDynamo(templateManifest,
                                 commName,
                                 nextVersion.commType,
                                 nextVersion.brand,
                                 publishedBy,
                                 templates)
    } yield nextVersion
  }

  def validateAndUploadNewTemplate(templateManifest: TemplateManifest,
                                   commName: String,
                                   commType: CommType,
                                   brand: Brand,
                                   uploadedFiles: List[UploadedFile],
                                   publishedBy: String,
                                   context: TemplatesContext): TemplateOp[List[String]] = {
    for {
      _                      <- validateTemplateDoesNotExist(templateManifest, commName)
      templateFiles          <- validateTemplate(templateManifest, uploadedFiles)
      processedUploadResults <- uploadProcessedTemplateToS3(templateManifest, templateFiles, publishedBy)
      rawUploadResults       <- uploadRawTemplateToS3(templateManifest, templateFiles, publishedBy)
      channels               <- getChannels(templateManifest, context)
      _                      <- writeTemplateToDynamo(templateManifest, commName, commType, brand, publishedBy, channels)
    } yield rawUploadResults ++ processedUploadResults
  }

  def uploadProcessedTemplateToS3(templateManifest: TemplateManifest,
                                  uploadedFiles: List[UploadedTemplateFile],
                                  publishedBy: String): TemplateOp[List[String]] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    for {
      processedFiles <- processTemplateAssets(templateManifest, uploadedFiles)
      assetsUploadResults <- processedFiles.assetFiles.traverse(file =>
        uploadTemplateAssetFileToS3(templateManifest, file, publishedBy))
      furtherProcessedFiles <- injectChannelSpecificStuff(processedFiles)
      templateFilesUploadResults <- furtherProcessedFiles.templateFiles.traverse(file =>
        uploadProcessedTemplateFileToS3(templateManifest, file, publishedBy))
    } yield assetsUploadResults ++ templateFilesUploadResults
  }

  def uploadRawTemplateToS3(templateManifest: TemplateManifest,
                            uploadedFiles: List[UploadedTemplateFile],
                            publishedBy: String): TemplateOp[List[String]] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    uploadedFiles.traverse(file => uploadRawTemplateFileToS3(templateManifest, file, publishedBy))
  }

  def getChannels(templateManifest: TemplateManifest, context: TemplatesContext): TemplateOp[List[Channel]] = {
    liftF(GetChannels(templateManifest, context))
  }

  def processTemplateAssets(templateManifest: TemplateManifest,
                            uploadedFiles: List[UploadedTemplateFile]): TemplateOp[ProcessedFiles] = {
    liftF(ProcessTemplateAssets(templateManifest, uploadedFiles))
  }

  def injectChannelSpecificStuff(processedFiles: ProcessedFiles): TemplateOp[ProcessedFiles] = {
    liftF(InjectChannelSpecificScript(processedFiles))
  }

  def getNextTemplateSummary(templateId: String): TemplateOp[TemplateSummary] = {
    liftF(GetNextTemplateSummary(templateId))
  }

  def writeTemplateToDynamo(templateManifest: TemplateManifest,
                            commName: String,
                            commType: CommType,
                            brand: Brand,
                            publishedBy: String,
                            channels: List[Channel]) = {
    liftF(UploadTemplateToDynamo(templateManifest, commName, commType, brand, publishedBy, channels))
  }

  def uploadRawTemplateFileToS3(templateManifest: TemplateManifest,
                                uploadedFile: UploadedTemplateFile,
                                publishedBy: String): TemplateOp[String] =
    liftF(UploadRawTemplateFileToS3(templateManifest, uploadedFile, publishedBy))

  def uploadProcessedTemplateFileToS3(templateManifest: TemplateManifest,
                                      uploadedFile: UploadedTemplateFile,
                                      publishedBy: String): TemplateOp[String] =
    liftF(UploadProcessedTemplateFileToS3(templateManifest, uploadedFile, publishedBy))

  def uploadTemplateAssetFileToS3(templateManifest: TemplateManifest,
                                  uploadedFile: UploadedTemplateFile,
                                  publishedBy: String): TemplateOp[String] =
    liftF(UploadTemplateAssetFileToS3(templateManifest, uploadedFile, publishedBy))

  def validateTemplate(templateManifest: TemplateManifest,
                       uploadedFiles: List[UploadedFile]): TemplateOp[List[UploadedTemplateFile]] =
    liftF(ValidateTemplate(templateManifest, uploadedFiles))

  def validateTemplateDoesNotExist(templateManifest: TemplateManifest, commName: String): TemplateOp[Unit] =
    liftF(ValidateTemplateDoesNotExist(templateManifest, commName))

  def retrieveTemplate(templateManifest: TemplateManifest): TemplateOp[ZippedRawTemplate] =
    for {
      template   <- retrieveTemplateFromS3(templateManifest)
      zippedFile <- compressTemplatesToZipFile(template)
    } yield ZippedRawTemplate(zippedFile)

}
