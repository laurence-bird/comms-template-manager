import cats.data._

import scala.util.matching.Regex

package object templates {

  type TemplateErrors[A] = ValidatedNel[String, A]

  sealed trait TemplateFileType { def regex: Regex }

  object TemplateFileType {

    object Email {
      case object Subject  extends TemplateFileType { val regex = "^email/subject.txt$".r }
      case object HtmlBody extends TemplateFileType { val regex = "^email/body.html$".r   }
      case object TextBody extends TemplateFileType { val regex = "^email/body.txt$".r    }
      case object Sender   extends TemplateFileType { val regex = "^email/sender.txt$".r  }
      case object Assets   extends TemplateFileType { val regex = "^email/assets/*".r     }

      val nonAssetFiles = List(Subject, HtmlBody, TextBody, Sender)
      val allFiles      = Assets :: nonAssetFiles
    }

    object SMS {
      case object TextBody extends TemplateFileType { val regex = "^sms/body.txt".r }

      val allFiles = List(TextBody)
    }

    val nonAssetFiles = Email.nonAssetFiles ++ SMS.allFiles
    val allFiles      = Email.allFiles ++ SMS.allFiles

  }

  object UploadedTemplateFile {
    def fromUploadedFile(uploadedFile: UploadedFile): Option[UploadedTemplateFile] =
      TemplateFileType.allFiles.collectFirst {
        case fileType if fileType.regex.pattern.matcher(uploadedFile.path).find =>
          UploadedTemplateFile(fileType, uploadedFile.contents)
      }
  }

  case class UploadedTemplateFile(fileType: TemplateFileType, contents: Array[Byte])

  object UploadedFile {
    def extractAllExpectedFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] =
      filter(uploadedFiles, TemplateFileType.allFiles)

    def extractAllEmailFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] =
      filter(uploadedFiles, TemplateFileType.Email.allFiles)

    def extractNonAssetFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] =
      filter(uploadedFiles, TemplateFileType.nonAssetFiles)

    def extractNonAssetEmailFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] =
      filter(uploadedFiles, TemplateFileType.Email.nonAssetFiles)

    def extractAssetEmailFiles(uploadedFiles: List[UploadedFile]): List[UploadedFile] =
      filter(uploadedFiles, List(TemplateFileType.Email.Assets))

    private def filter(uploadedFiles: List[UploadedFile], subset: Iterable[TemplateFileType]): List[UploadedFile] =
      uploadedFiles.filter(uploadedFile => subset.exists(_.regex.pattern.matcher(uploadedFile.path).find))

  }

  case class UploadedFile(path: String, contents: Array[Byte]) {
    override def toString: String = {
      s"UploadedFile($path,${new String(contents)})"
    }
  }

}
