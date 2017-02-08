package logic

import com.ovoenergy.comms.model.CommManifest
import logic.TemplateOp.TemplateFiles
import models.TemplateVersion

sealed trait TemplateOpA[T]

case class  RetrieveTemplateFromS3(commManifest: CommManifest) extends TemplateOpA[TemplateFiles]

case class RetrieveTemplateVersionFromDynamo(commManifest: CommManifest) extends TemplateOpA[TemplateVersion]

case class CompressTemplates(templateFiles: TemplateFiles) extends TemplateOpA[Array[Byte]]