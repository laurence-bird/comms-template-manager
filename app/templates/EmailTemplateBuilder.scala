package templates

import cats.Apply
import cats.data.{NonEmptyList, Validated}
import com.ovoenergy.comms.model.{Channel, CommManifest}
import com.ovoenergy.comms.templates.ErrorsOr
import com.ovoenergy.comms.templates.model.FileFormat
import com.ovoenergy.comms.templates.model.template.files.TemplateFile
import com.ovoenergy.comms.templates.model.template.files.email.EmailTemplateFiles
import com.ovoenergy.comms.templates.retriever.TemplatesRetriever
import org.apache.commons.compress.utils.IOUtils
import templates.TemplateValidator._

class EmailTemplateBuilder(files: List[EmailTemplateFile]) extends TemplatesRetriever[EmailTemplateFiles] {
  override def getTemplate(commManifest: CommManifest): Option[TemplateErrors[EmailTemplateFiles]] = {

    val subject: TemplateErrors[TemplateFile] =
      Validated.fromOption(
        files
          .find(_.fileType == Subject)
          .map(file => TemplateFile(commManifest.commType, Channel.Email, FileFormat.Text, new String(file.contents))),
        ifNone = NonEmptyList.of(s"No subject file has been provided in template")
      )
    val htmlBody: TemplateErrors[TemplateFile] =
      Validated.fromOption(
        files
          .find(_.fileType == HtmlBody)
          .map(file => TemplateFile(commManifest.commType, Channel.Email, FileFormat.Html, new String(file.contents))),
        ifNone = NonEmptyList.of(s"No html body file has been provided in template")
      )
    val textBody: Option[TemplateFile] = files
      .find(_.fileType == TextBody)
      .map(file => TemplateFile(commManifest.commType, Channel.Email, FileFormat.Text, new String(file.contents)))
    val customSender: Option[String] = files.find(_.fileType == Sender).map(file => new String(file.contents))

    Some(
      Apply[TemplateErrors]
        .map2(subject, htmlBody) {
          case (sub, html) =>
            EmailTemplateFiles(
              subject = sub,
              htmlBody = html,
              textBody = textBody,
              sender = customSender
            )
        })

  }
}
