package logic

import cats.Id
import cats.data.NonEmptyList
import com.ovoenergy.comms.model._
import com.ovoenergy.comms.templates.TemplatesContext
import com.ovoenergy.comms.templates.model.template.processed.CommTemplate
import logic.TemplateOp.TemplateFiles
import models.{TemplateSummary, TemplateVersion}
import templates.AssetProcessing.ProcessedFiles
import templates.{UploadedFile, UploadedTemplateFile}

sealed trait TemplateOpA[T]

case class RetrieveTemplateFromS3(templateManifest: TemplateManifest) extends TemplateOpA[TemplateFiles]

case class RetrieveTemplateVersionFromDynamo(templateManifest: TemplateManifest) extends TemplateOpA[TemplateVersion]

case class CompressTemplates(templateFiles: TemplateFiles) extends TemplateOpA[Array[Byte]]

case class RetrieveAllTemplateVersions(templateId: String, commName: String) extends TemplateOpA[Seq[TemplateVersion]]

case object ListTemplateSummaries extends TemplateOpA[Seq[TemplateSummary]]

case class ProcessTemplateAssets(templateManifest: TemplateManifest, uploadedFiles: List[UploadedTemplateFile])
    extends TemplateOpA[ProcessedFiles]

case class InjectChannelSpecificScript(processedFiles: ProcessedFiles) extends TemplateOpA[ProcessedFiles]

case class UploadRawTemplateFileToS3(templateManifest: TemplateManifest,
                                     uploadedFile: UploadedTemplateFile,
                                     publishedBy: String)
    extends TemplateOpA[String]

case class UploadProcessedTemplateFileToS3(templateManifest: TemplateManifest,
                                           uploadedFile: UploadedTemplateFile,
                                           publishedBy: String)
    extends TemplateOpA[String]

case class UploadTemplateAssetFileToS3(templateManifest: TemplateManifest,
                                       uploadedFile: UploadedTemplateFile,
                                       publishedBy: String)
    extends TemplateOpA[String]

case class ValidateTemplate(templateManifest: TemplateManifest, uploadedFiles: List[UploadedFile])
    extends TemplateOpA[List[UploadedTemplateFile]]

case class ValidateTemplateDoesNotExist(templateManifest: TemplateManifest, commName: String) extends TemplateOpA[Unit]

case class UploadTemplateToDynamo(templateManifest: TemplateManifest,
                                  commName: String,
                                  commType: CommType,
                                  brand: Brand,
                                  publishedBy: String,
                                  channels: List[Channel])
    extends TemplateOpA[Unit]

case class GetNextTemplateSummary(commName: String) extends TemplateOpA[TemplateSummary]

case class GetChannels(templateManifest: TemplateManifest, context: TemplatesContext)
    extends TemplateOpA[List[Channel]]
