package templates

import java.nio.charset.StandardCharsets

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated}
import cats.syntax.cartesian._
import com.ovoenergy.comms.model.{Channel, CommManifest}
import com.ovoenergy.comms.templates.model.FileFormat
import com.ovoenergy.comms.templates.model.template.files.TemplateFile
import com.ovoenergy.comms.templates.model.template.files.email.EmailTemplateFiles
import com.ovoenergy.comms.templates.model.template.files.sms.SMSTemplateFiles
import com.ovoenergy.comms.templates.retriever.TemplatesRetriever

class TemplateBuilder(files: List[UploadedFile]) extends TemplatesRetriever {

  override def getEmailTemplate(commManifest: CommManifest): Option[TemplateErrors[EmailTemplateFiles]] = {
    val subject: Option[TemplateFile] = files.collectFirst {
      case uploadedFile if uploadedFile.templateFileType.contains(EmailSubject) =>
        TemplateFile(commManifest.commType,
                     Channel.Email,
                     FileFormat.Text,
                     new String(uploadedFile.contents, StandardCharsets.UTF_8))
    }
    val htmlBody: Option[TemplateFile] = files.collectFirst {
      case uploadedFile if uploadedFile.templateFileType.contains(EmailHtmlBody) =>
        TemplateFile(commManifest.commType,
                     Channel.Email,
                     FileFormat.Html,
                     new String(uploadedFile.contents, StandardCharsets.UTF_8))
    }
    val textBody: Option[TemplateFile] = files.collectFirst {
      case uploadedFile if uploadedFile.templateFileType.contains(EmailTextBody) =>
        TemplateFile(commManifest.commType,
                     Channel.Email,
                     FileFormat.Text,
                     new String(uploadedFile.contents, StandardCharsets.UTF_8))
    }
    val customSender: Option[String] = files.collectFirst {
      case uploadedFile if uploadedFile.templateFileType.contains(EmailSender) =>
        new String(uploadedFile.contents, StandardCharsets.UTF_8)
    }

    val templateOrError: TemplateErrors[EmailTemplateFiles] = {
      val s =
        Validated.fromOption(subject, ifNone = NonEmptyList.of(s"No email subject file has been provided in template"))
      val h =
        Validated.fromOption(htmlBody,
                             ifNone = NonEmptyList.of(s"No email html body file has been provided in template"))

      (s |@| h).map {
        case (sub, html) =>
          EmailTemplateFiles(
            subject = sub,
            htmlBody = html,
            textBody = textBody,
            sender = customSender
          )
      }
    }

    (subject, htmlBody, textBody, customSender) match {
      case (None, None, None, None) => None // if none of the files exist, there is no email template
      case _                        => Some(templateOrError)
    }
  }

  override def getSMSTemplate(commManifest: CommManifest): Option[TemplateErrors[SMSTemplateFiles]] = {
    val textBody: Option[TemplateFile] = files
      .find(_.templateFileType.contains(SMSTextBody))
      .map(file => TemplateFile(commManifest.commType, Channel.Email, FileFormat.Html, new String(file.contents)))

    textBody.map(tb => Valid(SMSTemplateFiles(textBody = tb)))
  }

}
