package templates

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated}
import cats.implicits._
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
      case uploaded @ UploadedTemplateFile(_, _, Email, Subject, _) =>
        TemplateFile(commManifest.commType, Email, FileFormat.Text, uploaded.utf8Content)
    }
    val htmlBody: Option[TemplateFile] = files.collectFirst {
      case uploaded @ UploadedTemplateFile(_, _, Email, HtmlBody, _) =>
        TemplateFile(commManifest.commType, Email, FileFormat.Html, uploaded.utf8Content)
    }
    val textBody: Option[TemplateFile] = files.collectFirst {
      case uploaded @ UploadedTemplateFile(_, _, Email, TextBody, _) =>
        TemplateFile(commManifest.commType, Email, FileFormat.Text, uploaded.utf8Content)
    }
    val customSender: Option[String] = files.collectFirst {
      case uploaded @ UploadedTemplateFile(_, _, Email, Sender, _) => uploaded.utf8Content
    }

    val templateOrError: TemplateErrors[EmailTemplateFiles] = {
      val s: Validated[NonEmptyList[String], TemplateFile] =
        Validated.fromOption(subject, ifNone = NonEmptyList.of(s"No email subject file has been provided in template"))
      val h =
        Validated.fromOption(htmlBody,
                             ifNone = NonEmptyList.of(s"No email html body file has been provided in template"))
      (s, h).mapN {
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
      case uploaded @ UploadedTemplateFile(_, _, SMS, TextBody, _) =>
        TemplateFile(commManifest.commType, SMS, FileFormat.Text, uploaded.utf8Content)
    }

    textBody.map(tb => Valid(SMSTemplateFiles(textBody = tb)))
  }

  override def getPrintTemplate(commManifest: CommManifest): Option[TemplateErrors[PrintTemplateFiles]] = {
    val htmlBody: Option[TemplateFile] = files.collectFirst {
      case uploaded @ UploadedTemplateFile(_, _, Print, HtmlBody, _) =>
        TemplateFile(commManifest.commType, Print, FileFormat.Html, uploaded.utf8Content)
    }

    htmlBody.map(tb => Valid(PrintTemplateFiles(body = tb)))
  }
}
