package logic

import cats.free.Free
import cats.free.Free._
import com.ovoenergy.comms.model.{CommManifest, CommType}
import models.{TemplateSummary, TemplateVersion, ZippedRawTemplate}
import templates.AssetProcessing.ProcessedFiles
import templates.UploadedFile

object TemplateOp {

  type TemplateOp[A] = Free[TemplateOpA, A]

  type TemplateFiles = Map[String, Array[Byte]]

  def retrieveTemplateFromS3(commManifest: CommManifest): TemplateOp[TemplateFiles] =
    liftF(RetrieveTemplateFromS3(commManifest))

  def retrieveTemplateInfo(commManifest: CommManifest): TemplateOp[TemplateVersion] =
    liftF(RetrieveTemplateVersionFromDynamo(commManifest))

  def retrieveAllTemplateVersions(commName: String): TemplateOp[Seq[TemplateVersion]] =
    liftF(RetrieveAllTemplateVersions(commName))

  def compressTemplatesToZipFile(templateFiles: TemplateFiles): TemplateOp[Array[Byte]] =
    liftF(CompressTemplates(templateFiles))

  def listTemplateSummaries(): TemplateOp[Seq[TemplateSummary]] =
    liftF(ListTemplateSummaries())

  def validateAndUploadExistingTemplate(commName: String, commType: String, uploadedFiles: List[UploadedFile]): TemplateOp[String] = {
    for {
      nextVersion  <- getNextTemplateVersion(commName, commType)
      commManifest  = CommManifest(CommType.CommTypeFromValue(commType), commName, nextVersion)
      _            <- validateTemplate(commManifest, uploadedFiles)
      _            <- writeTemplateToDynamo(commManifest)
      _            <- uploadProcessedTemplateToS3(commManifest, uploadedFiles)
      _            <- uploadRawTemplateToS3(commManifest, uploadedFiles)
    } yield nextVersion
  }

  def validateAndUploadNewTemplate(commManifest: CommManifest, uploadedFiles: List[UploadedFile]): TemplateOp[List[String]] = {
    for {
      _                      <- validateTemplateDoesNotExist(commManifest)
      _                      <- validateTemplate(commManifest, uploadedFiles)
      _                      <- writeTemplateToDynamo(commManifest)
      processedUploadResults <- uploadProcessedTemplateToS3(commManifest, uploadedFiles)
      rawUploadResults       <- uploadRawTemplateToS3(commManifest, uploadedFiles)
    } yield rawUploadResults ++ processedUploadResults
  }

  def uploadProcessedTemplateToS3(commManifest: CommManifest, uploadedFiles: List[UploadedFile]): TemplateOp[List[String]] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    for {
      processedFiles             <- processTemplateFiles(commManifest, uploadedFiles)
      assetsUploadResults        <- processedFiles.assetFiles.traverseU(file => uploadTemplateAssetFileToS3(commManifest, file))
      templateFilesUploadResults <- processedFiles.templateFiles.traverseU(file => uploadProcessedTemplateFileToS3(commManifest, file))
    } yield assetsUploadResults ++ templateFilesUploadResults
  }

  def uploadRawTemplateToS3(commManifest: CommManifest, uploadedFiles: List[UploadedFile]): TemplateOp[List[String]] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    uploadedFiles.traverseU(file => uploadRawTemplateFileToS3(commManifest, file))
  }

  def processTemplateFiles(commManifest: CommManifest, uploadedFiles: List[UploadedFile]): TemplateOp[ProcessedFiles] = {
    liftF(ProcessTemplateAssets(commManifest, uploadedFiles))
  }

  def getNextTemplateVersion(commName: String, commType: String): TemplateOp[String] = {
    liftF(GetNextTemplateVersion(commName, commType))
  }

  def writeTemplateToDynamo(commManifest: CommManifest) = {
    liftF(UploadTemplateToDynamo(commManifest))
  }

  def uploadRawTemplateFileToS3(commManifest: CommManifest, uploadedFile: UploadedFile): TemplateOp[String] =
    liftF(UploadRawTemplateFileToS3(commManifest, uploadedFile))

  def uploadProcessedTemplateFileToS3(commManifest: CommManifest, uploadedFile: UploadedFile): TemplateOp[String] =
    liftF(UploadProcessedTemplateFileToS3(commManifest, uploadedFile))

  def uploadTemplateAssetFileToS3(commManifest: CommManifest, uploadedFile: UploadedFile): TemplateOp[String] =
    liftF(UploadTemplateAssetFileToS3(commManifest, uploadedFile))

  def validateTemplate(commManifest: CommManifest, uploadedFiles: List[UploadedFile]): TemplateOp[Unit] =
    liftF(ValidateTemplate(commManifest, uploadedFiles))

  def validateTemplateDoesNotExist(commManifest: CommManifest): TemplateOp[Unit] =
    liftF(ValidateTemplateDoesNotExist(commManifest))

  def retrieveTemplate(commManifest: CommManifest): TemplateOp[ZippedRawTemplate] =
    for {
      template    <- retrieveTemplateFromS3(commManifest)
      zippedFile  <- compressTemplatesToZipFile(template)
    } yield ZippedRawTemplate(zippedFile)

}
