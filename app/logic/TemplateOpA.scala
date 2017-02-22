package logic

import com.ovoenergy.comms.model.CommManifest
import logic.TemplateOp.TemplateFiles
import models.{TemplateSummary, TemplateVersion}
import templates.UploadedFile

sealed trait TemplateOpA[T]

case class RetrieveTemplateFromS3(commManifest: CommManifest) extends TemplateOpA[TemplateFiles]

case class RetrieveTemplateVersionFromDynamo(commManifest: CommManifest) extends TemplateOpA[TemplateVersion]

case class CompressTemplates(templateFiles: TemplateFiles) extends TemplateOpA[Array[Byte]]

case class RetrieveAllTemplateVersions(commName: String) extends TemplateOpA[Seq[TemplateVersion]]

case class ListTemplateSummaries() extends TemplateOpA[Seq[TemplateSummary]]

case class UploadTemplateFileToS3Raw(commManifest: CommManifest, uploadedFile: UploadedFile) extends TemplateOpA[String]

case class ValidateTemplate(commManifest: CommManifest, uploadedFiles: List[UploadedFile]) extends TemplateOpA[Unit]

case class ValidateTemplateDoesNotExist(commManifest: CommManifest) extends TemplateOpA[Unit]

case class UploadTemplateToDynamo(commManifest: CommManifest) extends TemplateOpA[Unit]