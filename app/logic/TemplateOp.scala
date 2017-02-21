package logic

import cats.free.Free
import cats.free.Free._
import com.ovoenergy.comms.model.CommManifest
import models.{TemplateSummary, TemplateVersion, ZippedRawTemplate}
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

  def validateAndUploadTemplate(commManifest: CommManifest, uploadedFiles: List[UploadedFile]): TemplateOp[List[String]] = {
    for {
      _ <- validateTemplate(commManifest, uploadedFiles)
      uploadResults <- uploadTemplate(commManifest, uploadedFiles)
    } yield uploadResults
  }

  def uploadTemplate(commManifest: CommManifest, uploadedFiles: List[UploadedFile]): TemplateOp[List[String]] = {
    import cats.syntax.traverse._
    import cats.instances.list._
    uploadedFiles.traverseU(file => uploadTemplateFile(commManifest, file))
  }

  def uploadTemplateFile(commManifest: CommManifest, uploadedFile: UploadedFile): TemplateOp[String] =
    liftF(UploadTemplateFile(commManifest, uploadedFile))

  def validateTemplate(commManifest: CommManifest, uploadedFiles: List[UploadedFile]): TemplateOp[Unit] =
    liftF(ValidateTemplate(commManifest, uploadedFiles))

  def retrieveTemplate(commManifest: CommManifest): TemplateOp[ZippedRawTemplate] =
    for {
      template    <- retrieveTemplateFromS3(commManifest)
      zippedFile  <- compressTemplatesToZipFile(template)
    } yield ZippedRawTemplate(zippedFile)

}
