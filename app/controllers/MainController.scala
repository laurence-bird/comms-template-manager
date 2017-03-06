package controllers

import java.io.ByteArrayInputStream
import java.util.zip.{ZipEntry, ZipFile}

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import aws.Interpreter.ErrorsOr
import cats.instances.either._
import cats.~>
import com.gu.googleauth.GoogleAuthConfig
import com.ovoenergy.comms.model.{CommManifest, CommType}
import logic.{TemplateOp, TemplateOpA}
import models.ZippedRawTemplate
import org.apache.commons.compress.utils.IOUtils
import org.slf4j.LoggerFactory
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSClient
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import templates.UploadedFile

import scala.collection.JavaConversions._

class MainController(val authConfig: GoogleAuthConfig,
                     val wsClient: WSClient,
                     val enableAuth: Boolean,
                     interpreter: ~>[TemplateOpA, ErrorsOr],
                     val messagesApi: MessagesApi) extends AuthActions with Controller with I18nSupport {

  val log = LoggerFactory.getLogger("MainController")

  val healthcheck = Action { Ok("OK") }

  val index = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

  def getTemplateVersion(commName: String, version: String) = Authenticated{ request =>
    TemplateOp.retrieveTemplate(CommManifest(CommType.Service, commName, version)).foldMap(interpreter) match {
      case Left(err) => NotFound(s"Failed to retrieve template: $err")
      case Right(res: ZippedRawTemplate) =>
        val dataContent: Source[ByteString, _] = StreamConverters.fromInputStream(() => new ByteArrayInputStream(res.templateFiles))
        Ok.chunked(dataContent).withHeaders(("Content-Disposition", s"attachment; filename=$commName-$version.zip")).as("application/zip")
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
      case Left(errs)      => NotFound(errs.head)
      case Right(versions) => Ok(views.html.templateVersions(versions, commName))
    }
  }

  def publishNewTemplateGet = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.publishNewTemplate("inprogress", List[String](), None, None))
  }

  def publishExistingTemplateGet(commName: String) = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.publishExistingTemplate("inprogress", List[String](), commName))
  }

  def publishNewTemplatePost = Authenticated(parse.multipartFormData) { implicit multipartFormRequest =>
    implicit val user = multipartFormRequest.user

    val result = for {
      commName <- multipartFormRequest.body.dataParts.get("commName")
      commType <- multipartFormRequest.body.dataParts.get("commType")
      templateFile <- multipartFormRequest.body.file("templateFile")
    } yield {
      val commManifest = CommManifest(CommType.CommTypeFromValue(commType.head), commName.head, "1.0")


      val uploadedFiles = extractUploadedFiles(templateFile)
      TemplateOp.validateAndUploadNewTemplate(commManifest, uploadedFiles, user.username).foldMap(interpreter) match {
        case Right(_)     => Ok(views.html.publishNewTemplate("ok", List(s"Template published: $commManifest"), Some(commName.head), Some(commType.head)))
        case Left(errors) => Ok(views.html.publishNewTemplate("error", errors.toList, Some(commName.head), Some(commType.head)))
      }
    }
      result.getOrElse {
      Ok(views.html.publishNewTemplate("error", List("Missing required fields"), None, None))
    }
  }

  def publishExistingTemplatePost(commName: String) = Authenticated(parse.multipartFormData) { implicit multipartFormRequest =>
    implicit val user = multipartFormRequest.user

    multipartFormRequest.body.file("templateFile").map { templateFile =>
      val uploadedFiles = extractUploadedFiles(templateFile)
      TemplateOp.validateAndUploadExistingTemplate(commName, uploadedFiles, user.username).foldMap(interpreter) match {
        case Right(newVersion)  => Ok(views.html.publishExistingTemplate("ok", List(s"Template published: $newVersion"), commName))
        case Left(errors)       => Ok(views.html.publishExistingTemplate("error", errors.toList, commName))
      }
    }.getOrElse {
      Ok(views.html.publishExistingTemplate("error", List("Unknown issue accessing zip file"), commName))
    }
  }

  private def extractUploadedFiles(templateFile: FilePart[TemporaryFile]): List[UploadedFile] = {
    val zip = new ZipFile(templateFile.ref.file)
    val zipEntries: collection.Iterator[ZipEntry] = zip.entries

    zipEntries.filter(!_.isDirectory)
      .foldLeft(List[UploadedFile]())((list, zipEntry) => {
        val inputStream = zip.getInputStream(zipEntry)
        try {
          val path = zipEntry.getName.replaceFirst("^/", "")
          UploadedFile(path, IOUtils.toByteArray(inputStream)) :: list
        } finally {
          IOUtils.closeQuietly(inputStream)
        }
      })
  }
}
