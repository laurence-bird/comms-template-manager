import java.io.InputStream
import cats.data._
import scala.util.matching.Regex

package object templates {

  type TemplateErrors[A] = ValidatedNel[String, A]

  sealed trait EmailTemplateFileType
  case object Subject  extends EmailTemplateFileType
  case object HtmlBody extends EmailTemplateFileType
  case object TextBody extends EmailTemplateFileType
  case object Sender   extends EmailTemplateFileType
  case object Assets   extends EmailTemplateFileType

  object EmailTemplateFile {
    def fromUploadedFile(uploadedFile: UploadedFile): Option[EmailTemplateFile] = {
      TemplateFileRegexes.Email.allFiles
        .find(templateFileRegex => templateFileRegex.regex.findFirstMatchIn(uploadedFile.path).isDefined)
        .map(templateFileRegex => EmailTemplateFile(templateFileRegex.fileType, uploadedFile.contents))
    }
  }

  case class EmailTemplateFile(fileType: EmailTemplateFileType, contents: Array[Byte])

  case class TemplateFileRegex(fileType: EmailTemplateFileType, regex: Regex)
  object TemplateFileRegexes {
    object Email {
      val subject  = TemplateFileRegex(Subject, "^email/subject.txt".r)
      val htmlBody = TemplateFileRegex(HtmlBody, "^email/body.html".r)
      val textBody = TemplateFileRegex(TextBody, "^email/body.txt".r)
      val sender   = TemplateFileRegex(Sender, "^email/sender.txt".r)
      val assets   = TemplateFileRegex(Assets, "^email/assets/.*".r)

      def allRegexes    = List(subject.regex, htmlBody.regex, textBody.regex, sender.regex, assets.regex)
      def nonAssetFiles = List(subject, htmlBody, textBody, sender)
      def allFiles      = List(subject, htmlBody, textBody, sender, assets)
    }
  }

  object UploadedFile {
    def extractAllEmailFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] = {
      uploadedFiles
        .filter(uploadedFile =>
          TemplateFileRegexes.Email.allRegexes.exists(_.findFirstMatchIn(uploadedFile.path).isDefined))
    }

    def extractNonAssetEmailFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] = {
      TemplateFileRegexes.Email.nonAssetFiles
        .flatMap { templateFileRegex =>
          uploadedFiles
            .find(uploadedFile => templateFileRegex.regex.findFirstMatchIn(uploadedFile.path).isDefined)
        }
    }

    def extractAssetEmailFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] = {
      val assetFilePathsRegex = TemplateFileRegexes.Email.assets.regex
      uploadedFiles
        .filter(uploadedFile => assetFilePathsRegex.findFirstIn(uploadedFile.path).isDefined)
    }
  }

  case class UploadedFile(path: String, contents: Array[Byte]) {
    override def toString: String = {
      s"UploadedFile($path,${new String(contents)})"
    }
  }

}
