import cats.data._
import com.ovoenergy.comms.model._
import play.api.libs.MimeTypes

import scala.util.matching.Regex

package object templates {
  type TemplateErrors[A] = ValidatedNel[String, A]

  sealed trait FileType

  case object Subject  extends FileType
  case object HtmlBody extends FileType
  case object TextBody extends FileType
  case object Sender   extends FileType
  case object Asset    extends FileType

  case class UploadedTemplateFile(path: String,
                                  contents: Array[Byte],
                                  channel: Channel,
                                  fileType: FileType,
                                  contentType: Option[String])

  object UploadedFile {

    private sealed trait FileRegex { def regex: Regex; def channel: Channel; def fileType: FileType }
    private case object EmailSubjectRegex extends FileRegex {
      val regex = "^email/subject.txt$".r; val channel = Email; val fileType = Subject
    }
    private case object EmailHtmlBodyRegex extends FileRegex {
      val regex = "^email/body.html$".r; val channel = Email; val fileType = HtmlBody
    }
    private case object EmailTextBodyRegex extends FileRegex {
      val regex = "^email/body.txt$".r; val channel = Email; val fileType = TextBody
    }
    private case object EmailSenderRegex extends FileRegex {
      val regex = "^email/sender.txt$".r; val channel = Email; val fileType = Sender
    }
    private case object EmailAssetsRegex extends FileRegex {
      val regex = "^email/assets/*".r; val channel = Email; val fileType = Asset
    }

    private case object SMSTextBodyRegex extends FileRegex {
      val regex = "^sms/body.txt$".r; val channel = SMS; val fileType = TextBody
    }

    private case object PrintHtmlBodyRegex extends FileRegex {
      val regex = "^print/body.html$".r; val channel = Print; val fileType = HtmlBody
    }
    private case object PrintAssetsRegex extends FileRegex {
      val regex = "^print/assets/*".r; val channel = Print; val fileType = Asset
    }

    private val emailNonAssetFilesRegexes =
      List(EmailSubjectRegex, EmailHtmlBodyRegex, EmailTextBodyRegex, EmailSenderRegex)
    private val emailAllFilesRegexes      = EmailAssetsRegex :: emailNonAssetFilesRegexes
    private val smsAllFilesRegexes        = List(SMSTextBodyRegex)
    private val printNonAssetFilesRegexes = List(PrintHtmlBodyRegex)
    private val printAllFilesRegexes      = PrintAssetsRegex :: printNonAssetFilesRegexes

    private val nonAssetFilesRegexes = emailNonAssetFilesRegexes ++ smsAllFilesRegexes ++ printNonAssetFilesRegexes

    private val allFilesRegexes = emailAllFilesRegexes ++ smsAllFilesRegexes ++ printAllFilesRegexes

    def extractAllExpectedFiles(uploadedFiles: List[UploadedFile]): List[UploadedTemplateFile] =
      filter(uploadedFiles, allFilesRegexes)

    def extractNonAssetFiles(uploadedFiles: List[UploadedFile]): List[UploadedTemplateFile] =
      filter(uploadedFiles, nonAssetFilesRegexes)

    def extractAssetFiles(uploadedFiles: List[UploadedFile]): List[UploadedTemplateFile] =
      filter(uploadedFiles, List(EmailAssetsRegex, PrintAssetsRegex))

    private def filter(uploadedFiles: List[UploadedFile], regexes: Iterable[FileRegex]): List[UploadedTemplateFile] =
      uploadedFiles.flatMap { uploadedFile =>
        val mimeType = MimeTypes.forFileName(uploadedFile.path)
        regexes.collectFirst {
          case r if r.regex.pattern.matcher(uploadedFile.path).find =>
            UploadedTemplateFile(uploadedFile.path, uploadedFile.contents, r.channel, r.fileType, mimeType)
        }
      }

  }

  case class UploadedFile(path: String, contents: Array[Byte]) {

    override def toString: String = {
      s"UploadedFile($path,${new String(contents)})"
    }
  }

}
