package templates

import java.nio.charset.StandardCharsets

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated}
import cats.syntax.cartesian._
import com.ovoenergy.comms.model._
import com.ovoenergy.comms.templates.model.FileFormat
import com.ovoenergy.comms.templates.model.template.files.TemplateFile
import com.ovoenergy.comms.templates.model.template.files.email.EmailTemplateFiles
import com.ovoenergy.comms.templates.model.template.files.print.PrintTemplateFiles
import com.ovoenergy.comms.templates.model.template.files.sms.SMSTemplateFiles
import com.ovoenergy.comms.templates.retriever.TemplatesRetriever

class TemplateBuilder(files: List[UploadedTemplateFile]) extends TemplatesRetriever {

  override def getEmailTemplate(commManifest: CommManifest): Option[TemplateErrors[EmailTemplateFiles]] = {
    val subject: Option[TemplateFile] = files.collectFirst {
      case UploadedTemplateFile(_, contents, Email, Subject) =>
        TemplateFile(commManifest.commType, Email, FileFormat.Text, new String(contents, StandardCharsets.UTF_8))
    }
    val htmlBody: Option[TemplateFile] = files.collectFirst {
      case UploadedTemplateFile(_, contents, Email, HtmlBody) =>
        TemplateFile(commManifest.commType, Email, FileFormat.Html, new String(contents, StandardCharsets.UTF_8))
    }
    val textBody: Option[TemplateFile] = files.collectFirst {
      case UploadedTemplateFile(_, contents, Email, TextBody) =>
        TemplateFile(commManifest.commType, Email, FileFormat.Text, new String(contents, StandardCharsets.UTF_8))
    }
    val customSender: Option[String] = files.collectFirst {
      case UploadedTemplateFile(_, contents, Email, Sender) =>
        new String(contents, StandardCharsets.UTF_8)
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
    val textBody: Option[TemplateFile] = files.collectFirst {
      case UploadedTemplateFile(_, contents, SMS, TextBody) =>
        TemplateFile(commManifest.commType, SMS, FileFormat.Text, new String(contents, StandardCharsets.UTF_8))
    }

    textBody.map(tb => Valid(SMSTemplateFiles(textBody = tb)))
  }

  override def getPrintTemplate(commManifest: CommManifest): Option[TemplateErrors[PrintTemplateFiles]] = {
    val htmlBody: Option[TemplateFile] = files.collectFirst {
      case UploadedTemplateFile(_, contents, Print, TextBody) =>
        TemplateFile(commManifest.commType, Print, FileFormat.Text, new String(contents, StandardCharsets.UTF_8))
    }

    htmlBody.map(tb => Valid(PrintTemplateFiles(body = tb)))
  }
}
