package controllers

import com.gu.googleauth.{GoogleAuthConfig, GoogleGroupChecker, LoginSupport}
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

case class GroupsAuthConfig(acceptedGroups: Set[String], groupChecker: GoogleGroupChecker)

class AuthController(val authConfig: GoogleAuthConfig,
                     val wsClient: WSClient,
                     val groupsAuthConfig: Option[GroupsAuthConfig],
                     val controllerComponents: ControllerComponents)
    extends LoginSupport
    with BaseController {

  def login = Action.async { implicit request =>
    startGoogleLogin()
  }

  def oauth2Callback = Action.async { implicit request =>
    groupsAuthConfig match {
      case Some(config) =>
        // perform both authentication and Google Groups-based authorisation
        processOauth2Callback(config.acceptedGroups, config.groupChecker)
      case None =>
        // only authentication
        processOauth2Callback()
    }
  }

  def authError = Action { request =>
    val error = request.flash.get("error")
    Ok(views.html.authError(error))
  }
  override val defaultRedirectTarget = routes.MainController.listTemplates()
  override val failureRedirectTarget = routes.AuthController.authError()

}
