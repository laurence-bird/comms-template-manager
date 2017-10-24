package logic

import com.ovoenergy.comms.model.CommManifest
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


case class InjectChannelSpecificStuff(processedFiles: ProcessedFiles) extends TemplateOpA[ProcessedFiles]

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

case class UploadTemplateToDynamo(commManifest: CommManifest, publishedBy: String) extends TemplateOpA[Unit]

case class GetNextTemplateSummary(commName: String) extends TemplateOpA[TemplateSummary]
