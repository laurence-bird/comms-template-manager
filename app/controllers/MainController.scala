package controllers

import java.io.ByteArrayInputStream
import java.util.zip.{ZipEntry, ZipFile}

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import aws.Interpreter.ErrorsOr
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
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
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
    Ok(views.html.publishNewTemplate("ok", List[String](), "", ""))
  }

  def publishExistingTemplateGet(commName: String, commType: String) = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.publishExistingTemplate("ok", List[String](), commName, commType))
  }

  def publishNewTemplatePost = Authenticated(parse.multipartFormData) { implicit multipartFormRequest =>
    implicit val user = multipartFormRequest.user

    val commName = multipartFormRequest.body.dataParts.get("commName").get.head
    val commType = multipartFormRequest.body.dataParts.get("commType").get.head

    multipartFormRequest.body.file("templateFile").map { templateFile =>
      val commManifest = CommManifest(CommType.CommTypeFromValue(commType), commName, "1.0")
      val uploadedFiles = extractUploadedFiles(templateFile)
      TemplateOp.validateAndUploadNewTemplate(commManifest, uploadedFiles).foldMap(interpreter) match {
        case Right(_)     => Ok(views.html.publishNewTemplate("ok", List(s"Template published: $commManifest"), commName, commType))
        case Left(errors) => Ok(views.html.publishNewTemplate("error", errors.toList, commName, commType))
      }
    }.getOrElse {
      Ok(views.html.publishNewTemplate("error", List("Unknown issue accessing zip file"), commName, commType))
    }
  }

  def publishExistingTemplatePost(commName: String, commType: String) = Authenticated(parse.multipartFormData) { implicit multipartFormRequest =>
    implicit val user = multipartFormRequest.user

    multipartFormRequest.body.file("templateFile").map { templateFile =>
      val uploadedFiles = extractUploadedFiles(templateFile)
      TemplateOp.validateAndUploadExistingTemplate(commName, commType, uploadedFiles).foldMap(interpreter) match {
        case Right(newVersion)  => Ok(views.html.publishExistingTemplate("ok", List(s"Template published, version $newVersion"), commName, commType))
        case Left(errors)       => Ok(views.html.publishExistingTemplate("error", errors.toList, commName, commType))
      }
    }.getOrElse {
      Ok(views.html.publishExistingTemplate("error", List("Unknown issue accessing zip file"), commName, commType))
    }
  }

  private def extractUploadedFiles(templateFile: FilePart[TemporaryFile]): List[UploadedFile] = {
    val zip = new ZipFile(templateFile.ref.file)
    val zipEntries: collection.Iterator[ZipEntry] = zip.entries

    zipEntries.filter(!_.isDirectory)
      .foldLeft(List[UploadedFile]())((list, zipEntry) => {
        val inputStream = zip.getInputStream(zipEntry)
        try {
          UploadedFile(zipEntry.getName, IOUtils.toByteArray(inputStream)) :: list
        } finally {
          IOUtils.closeQuietly(inputStream)
        }
      })
  }
}
