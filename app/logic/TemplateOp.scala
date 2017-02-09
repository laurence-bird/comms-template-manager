package logic

import cats.free.Free
import cats.free.Free._
import com.ovoenergy.comms.model.CommManifest
import models.{TemplateSummary, TemplateVersion, ZippedRawTemplate}

object TemplateOp {

  type TemplateManager[A] = Free[TemplateOpA, A]

  type TemplateFiles = Map[String, Array[Byte]]

  def retrieveTemplateFromS3(commManifest: CommManifest): TemplateManager[TemplateFiles] =
    liftF(RetrieveTemplateFromS3(commManifest))

  def retrieveTemplateInfo(commManifest: CommManifest): TemplateManager[TemplateVersion] =
    liftF(RetrieveTemplateVersionFromDynamo(commManifest))

  def retrieveAllTemplateVersions(commName: String): TemplateManager[Seq[TemplateVersion]] =
    liftF(RetrieveAllTemplateVersions(commName))

  def compressTemplatesToZipFile(templateFiles: TemplateFiles): TemplateManager[Array[Byte]] =
    liftF(CompressTemplates(templateFiles))

  def listTemplateSummaries(): TemplateManager[Seq[TemplateSummary]] =
    liftF(ListTemplateSummaries())

  def retrieveTemplate(commManifest: CommManifest): TemplateManager[ZippedRawTemplate] =
    for {
      template    <- retrieveTemplateFromS3(commManifest)
      zippedFile  <- compressTemplatesToZipFile(template)
    } yield ZippedRawTemplate(zippedFile)

}
