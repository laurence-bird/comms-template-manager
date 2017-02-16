package logic

import aws.s3.S3FileDetails
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

case class UploadTemplate(s3File: S3FileDetails) extends TemplateOpA[String]

case class ValidateTemplate(commManifest: CommManifest, uploadedFiles: List[UploadedFile]) extends TemplateOpA[Unit]

