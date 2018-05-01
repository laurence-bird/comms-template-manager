package logic

import cats.Id
import cats.data.NonEmptyList
import com.ovoenergy.comms.model.{Channel, CommManifest}
import com.ovoenergy.comms.templates.TemplatesContext
import com.ovoenergy.comms.templates.model.template.processed.CommTemplate
import logic.TemplateOp.TemplateFiles
import models.{TemplateSummary, TemplateVersion}
import templates.AssetProcessing.ProcessedFiles
import templates.{UploadedFile, UploadedTemplateFile}

sealed trait TemplateOpA[T]

case class RetrieveTemplateFromS3(commManifest: CommManifest) extends TemplateOpA[TemplateFiles]

case class RetrieveTemplateVersionFromDynamo(commManifest: CommManifest) extends TemplateOpA[TemplateVersion]

case class CompressTemplates(templateFiles: TemplateFiles) extends TemplateOpA[Array[Byte]]

case class RetrieveAllTemplateVersions(commName: String) extends TemplateOpA[Seq[TemplateVersion]]

case object ListTemplateSummaries extends TemplateOpA[Seq[TemplateSummary]]

case class ProcessTemplateAssets(commManifest: CommManifest, uploadedFiles: List[UploadedTemplateFile])
    extends TemplateOpA[ProcessedFiles]

case class InjectChannelSpecificScript(processedFiles: ProcessedFiles) extends TemplateOpA[ProcessedFiles]

case class UploadRawTemplateFileToS3(commManifest: CommManifest,
                                     uploadedFile: UploadedTemplateFile,
                                     publishedBy: String)
    extends TemplateOpA[String]

case class UploadProcessedTemplateFileToS3(commManifest: CommManifest,
                                           uploadedFile: UploadedTemplateFile,
                                           publishedBy: String)
    extends TemplateOpA[String]

case class UploadTemplateAssetFileToS3(commManifest: CommManifest,
                                       uploadedFile: UploadedTemplateFile,
                                       publishedBy: String)
    extends TemplateOpA[String]

case class ValidateTemplate(commManifest: CommManifest, uploadedFiles: List[UploadedFile])
    extends TemplateOpA[List[UploadedTemplateFile]]

case class ValidateTemplateDoesNotExist(commManifest: CommManifest) extends TemplateOpA[Unit]

case class UploadTemplateToDynamo(commManifest: CommManifest, publishedBy: String, channels: List[Channel])
    extends TemplateOpA[Unit]

case class GetNextTemplateSummary(commName: String) extends TemplateOpA[TemplateSummary]

case class GetChannels(commManifest: CommManifest, context: TemplatesContext) extends TemplateOpA[List[Channel]]
