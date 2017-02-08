package controllers

import java.io.ByteArrayInputStream

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import aws.Interpreter.ErrorsOr
import cats.instances.either._
import cats.~>
import com.gu.googleauth.GoogleAuthConfig
import com.ovoenergy.comms.model.{CommManifest, CommType}
import logic.{TemplateOpA, TemplateOp}
import models.ZippedRawTemplate
import play.api.Logger
import play.api.libs.ws.WSClient
import play.api.mvc._

class MainController(val authConfig: GoogleAuthConfig,
                     val wsClient: WSClient,
                     val enableAuth: Boolean,
                     interpreter: ~>[TemplateOpA, ErrorsOr]) extends AuthActions with Controller {


  val healthcheck = Action { Ok("OK") }

  val index = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

  def getTemplateVersion(commName: String, version: String) = Authenticated{ request =>
    TemplateOp.retrieveTemplate(CommManifest(CommType.Service, commName, version)).foldMap(interpreter) match {
      case Left(err) => Gone(s"Failed to retrieve template: $err")
      case Right(res: ZippedRawTemplate) => {
        val dataContent: Source[ByteString, _] = StreamConverters.fromInputStream(() => new ByteArrayInputStream(res.templateFiles))
        Ok.chunked(dataContent).withHeaders(("Content-Disposition", s"attachment; filename=$commName-$version.zip")).as("application/zip")
      }
    }
  }

  def listTemplates = TODO

  def listVersions(commName: String) = TODO

  def publishTemplate(commName: String, version: String) = TODO

}
