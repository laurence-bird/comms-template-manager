import cats.data._
import com.ovoenergy.comms.model.Channel

import scala.util.matching.Regex

package object templates {

  type TemplateErrors[A] = ValidatedNel[String, A]

  sealed trait TemplateFileType { def channel: Channel }

  case object EmailSubject  extends TemplateFileType { val channel = Channel.Email }
  case object EmailHtmlBody extends TemplateFileType { val channel = Channel.Email }
  case object EmailTextBody extends TemplateFileType { val channel = Channel.Email }
  case object EmailSender   extends TemplateFileType { val channel = Channel.Email }
  case object EmailAsset    extends TemplateFileType { val channel = Channel.Email }

  case object SMSTextBody extends TemplateFileType { val channel = Channel.SMS }

  object UploadedFile {

    private sealed trait FileRegex { def regex: Regex; def templateFileType: TemplateFileType }
    private case object EmailSubjectRegex extends FileRegex {
      val regex = "^email/subject.txt$".r; val templateFileType = EmailSubject
    }
    private case object EmailHtmlBodyRegex extends FileRegex {
      val regex = "^email/body.html$".r; val templateFileType = EmailHtmlBody
    }
    private case object EmailTextBodyRegex extends FileRegex {
      val regex = "^email/body.txt$".r; val templateFileType = EmailTextBody
    }
    private case object EmailSenderRegex extends FileRegex {
      val regex = "^email/sender.txt$".r; val templateFileType = EmailSender
    }
    private case object EmailAssetsRegex extends FileRegex {
      val regex = "^email/assets/*".r; val templateFileType = EmailAsset
    }

    private case object SMSTextBodyRegex extends FileRegex {
      val regex = "^sms/body.txt$".r; val templateFileType = SMSTextBody
    }

    private val emailNonAssetFilesRegexes =
      List(EmailSubjectRegex, EmailHtmlBodyRegex, EmailTextBodyRegex, EmailSenderRegex)
    private val emailAllFilesRegexes = EmailAssetsRegex :: emailNonAssetFilesRegexes
    private val smsAllFilesRegexes   = List(SMSTextBodyRegex)

    private val nonAssetFilesRegexes = emailNonAssetFilesRegexes ++ smsAllFilesRegexes
    private val allFilesRegexes      = emailAllFilesRegexes ++ smsAllFilesRegexes

    def extractAllExpectedFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] =
      filter(uploadedFiles, allFilesRegexes)

    def extractNonAssetFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] =
      filter(uploadedFiles, nonAssetFilesRegexes)

    def extractNonAssetEmailFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] =
      filter(uploadedFiles, emailNonAssetFilesRegexes)

    def extractAssetFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] =
      filter(uploadedFiles, List(EmailAssetsRegex))

    private def filter(uploadedFiles: List[UploadedFile], subset: Iterable[FileRegex]): List[UploadedFile] =
      uploadedFiles.filter(uploadedFile => subset.exists(_.regex.pattern.matcher(uploadedFile.path).find))

  }

  case class UploadedFile(path: String, contents: Array[Byte]) {
    lazy val templateFileType: Option[TemplateFileType] = {
      UploadedFile.allFilesRegexes.collectFirst {
        case fileRegex if fileRegex.regex.pattern.matcher(path).find =>
          fileRegex.templateFileType
      }
    }

    lazy val channel: Option[Channel] = templateFileType.map(_.channel)

    override def toString: String = {
      s"UploadedFile($path,${new String(contents)})"
    }
  }

}
