import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.nio.file.{Files, Path, StandardOpenOption}

import cats.data._
import cats.implicits._
import com.ovoenergy.comms.model._
import org.apache.commons.io.IOUtils
import play.api.libs.Files.TemporaryFile
import play.api.libs.MimeTypes

import scala.util.{Failure, Try}
import scala.util.matching.Regex
import java.nio.charset.StandardCharsets.UTF_8

import scala.annotation.tailrec
import scala.io.Source

package object templates {
  type TemplateErrors[A] = ValidatedNel[String, A]

  sealed trait FileType

  case object Subject  extends FileType
  case object HtmlBody extends FileType
  case object TextBody extends FileType
  case object Sender   extends FileType
  case object Asset    extends FileType

  case class UploadedTemplateFile(path: String,
                                  contents: Content,
                                  channel: Channel,
                                  fileType: FileType,
                                  contentType: Option[String]) {

    def byteArrayContents: Array[Byte] = contents.toByteArray

    def utf8Content: String = contents.toUtf8

    def mapContent(f: Array[Byte] => Array[Byte]): UploadedTemplateFile =
      copy(contents = contents.map(f))

    def mapUtf8Content(f: String => String): UploadedTemplateFile =
      copy(contents = contents.mapUtf8(f))
  }

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

  object Content {

    def apply(string: String): Content = ByteArrayContent(string.getBytes(UTF_8))

    def apply(data: Iterable[Byte]): Content = ByteArrayContent(data.toArray)

    def apply(temporaryFile: TemporaryFile): Content = PathContent(temporaryFile)

    def apply(path: Path): Content = {
      val destination = TemporaryFile()
      Files.copy(path, destination.file.toPath)
      PathContent(destination)
    }

  }

  sealed trait Content {

    override def finalize(): Unit = {
      clean()
      super.finalize()
    }

    def clean(): Unit = this match {
      case _: ByteArrayContent =>
      case pc: PathContent =>
        @tailrec
        def loop(pc: PathContent): Unit = {
          pc.temporaryFile.clean()
          pc.mappedFrom match {
            case None => ()
            case Some(x) =>
              loop(x)
          }
        }

        loop(pc)
    }

    def mapUtf8(f: String => String): Content =
      map(f.dimap[Array[Byte], Array[Byte]](new String(_, UTF_8))(_.getBytes(UTF_8)))

    def map(f: Array[Byte] => Array[Byte]): Content = this match {
      case ByteArrayContent(data) =>
        ByteArrayContent(f(data))
      case pc: PathContent =>
        val destination = TemporaryFile()
        destination.file.mkdirs()
        Files.copy(pc.path, destination.file.toPath)
        PathContent(destination, Some(pc))
    }

    def withContentStream[A](f: InputStream => A): A = {
      var in: InputStream = null
      try {
        in = newInputStream()
        f(in)
      } finally {
        IOUtils.closeQuietly(in)
      }
    }

    private def newInputStream(): InputStream = this match {
      case ByteArrayContent(data) => new ByteArrayInputStream(data)
      case pc: PathContent        => Files.newInputStream(pc.path)
    }

    /**
      * Avoid to use it in favour of map
      */
    def toByteArray: Array[Byte] = this match {
      case ByteArrayContent(data) => data
      case pc: PathContent        => Files.readAllBytes(pc.path)
    }

    /**
      * Avoid to use it in favour of mapUtf8
      */
    def toUtf8: String = new String(toByteArray, UTF_8)

  }

  case class ByteArrayContent(data: Array[Byte]) extends Content {
    override def toString: String = {
      s"ByteArrayContent(...)"
    }
  }

  case class PathContent(temporaryFile: TemporaryFile, mappedFrom: Option[PathContent] = None) extends Content {
    def path: Path = temporaryFile.file.toPath
  }

  case class UploadedFile(path: String, contents: Content) {

    def byteArrayContents: Array[Byte] = contents.toByteArray

    def utf8Content: String = contents.toUtf8

    def mapContent(f: Array[Byte] => Array[Byte]): UploadedFile =
      copy(contents = contents.map(f))

    def mapUtf8Content(f: String => String): UploadedFile =
      copy(contents = contents.mapUtf8(f))

    def clean(): Unit = contents.clean()

    override def toString: String = {
      s"UploadedFile($path, $contents)"
    }
  }

}
