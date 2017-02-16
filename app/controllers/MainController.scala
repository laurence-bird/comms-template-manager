package controllers

import java.io.{ByteArrayInputStream, File}
import java.util
import java.util.zip.{ZipEntry, ZipFile}

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import aws.Interpreter.ErrorsOr
import aws.s3.S3FileDetails
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.instances.either._
import cats.~>
import com.gu.googleauth.GoogleAuthConfig
import com.ovoenergy.comms.model.{CommManifest, CommType}
import logic.{TemplateOp, TemplateOpA}
import models.ZippedRawTemplate
import org.apache.commons.compress.utils.IOUtils
import org.slf4j.LoggerFactory
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.twirl.api.{Html, HtmlFormat}
import templates.UploadedFile

import scala.collection.JavaConversions._

class MainController(val authConfig: GoogleAuthConfig,
                     val wsClient: WSClient,
                     val enableAuth: Boolean,
                     interpreter: ~>[TemplateOpA, ErrorsOr],
                     val messagesApi: MessagesApi,
                     rawTemplatesBucket: String) extends AuthActions with Controller with I18nSupport {

  val log = LoggerFactory.getLogger("MainController")

  val healthcheck = Action { Ok("OK") }

  val index = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

  def getTemplateVersion(commName: String, version: String) = Authenticated{ request =>
    TemplateOp.retrieveTemplate(CommManifest(CommType.Service, commName, version)).foldMap(interpreter) match {
      case Left(err) => NotFound(s"Failed to retrieve template: $err")
      case Right(res: ZippedRawTemplate) => {
        val dataContent: Source[ByteString, _] = StreamConverters.fromInputStream(() => new ByteArrayInputStream(res.templateFiles))
        Ok.chunked(dataContent).withHeaders(("Content-Disposition", s"attachment; filename=$commName-$version.zip")).as("application/zip")
      }
    }
  }

  def listTemplates = Authenticated { request =>
    implicit val user = request.user
    TemplateOp.listTemplateSummaries().foldMap(interpreter) match {
      case Left(err)  => NotFound(s"Failed to retrieve templates: $err")
      case Right(res) => Ok(views.html.templateList(res))
    }
  }

  def listVersions(commName: String) = Authenticated { request =>
    implicit val user = request.user
    TemplateOp.retrieveAllTemplateVersions(commName).foldMap(interpreter) match {
      case Left(err)       => NotFound(err)
      case Right(versions) => Ok(views.html.templateVersions(versions, commName))
    }
  }

  def publish = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.publish("ok", ""))
  }

  def publishTemplate = Authenticated(parse.multipartFormData) { implicit multipartFormRequest =>
    implicit val user = multipartFormRequest.user

    val commName = multipartFormRequest.body.dataParts.get("commName").get.head
    val commType = multipartFormRequest.body.dataParts.get("commType").get.head
    multipartFormRequest.body.file("templateFile").map { templateFile =>

      val commManifest = CommManifest(CommType.CommTypeFromValue(commType), commName, "snapshot")
      val zip = new ZipFile(templateFile.ref.file)
      val zipEntries: collection.Iterator[ZipEntry] = zip.entries

      val uploadedFiles = zipEntries.filter(!_.isDirectory)
        .foldLeft(List[UploadedFile]())((list, zipEntry) => {
          val inputStream = zip.getInputStream(zipEntry)
          try {
            UploadedFile(zipEntry.getName, IOUtils.toByteArray(inputStream)) :: list
          } finally {
            IOUtils.closeQuietly(inputStream)
          }
        })


      val processResult = for {
        _ <- TemplateOp.validateTemplate(commManifest, uploadedFiles).foldMap(interpreter).right
        result <- uploadTemplate(commManifest, uploadedFiles).right
      } yield result

      processResult match {
        case Right(()) => Ok(views.html.publish("ok", stringToHtml("Template uploaded")))
        case Left(errors) => Ok(views.html.publish("error", stringToHtml(s"Problems found in template: \n$errors")))
      }
    }.getOrElse {
      Ok(views.html.publish("error", stringToHtml("Unknown issue accessing zip file")))
    }
  }

  private def uploadTemplate(commManifest: CommManifest, uploadedFiles: List[UploadedFile]): Either[String, Unit] = {
    val errors = uploadedFiles.flatMap(uploadedFile => {
      val key = s"${commManifest.commType.toString.toLowerCase}/${commManifest.name}/${commManifest.version}/${uploadedFile.path}"
      val s3File = S3FileDetails(uploadedFile.contents, key, rawTemplatesBucket)
      log.info(s3File.toString)
      TemplateOp.uploadTemplate(s3File).foldMap(interpreter) match {
        case Right(_) =>
          log.info(s"Uploaded ${uploadedFile.path}")
          None
        case Left(error) =>
          log.warn(s"Upload failed ${uploadedFile.path}: $error")
          Some(error)
      }
    })
    if (errors.isEmpty) Right(())
    else Left(errors.mkString("\n"))
  }

  private def stringToHtml(string: String) = {
    HtmlFormat.escape(string).toString.replace("\n", "<br />")
  }
}
