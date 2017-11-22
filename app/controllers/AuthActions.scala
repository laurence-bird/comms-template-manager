package controllers

import com.gu.googleauth.{Actions, AuthAction, UserIdentity}
import play.api.Logger
import play.api.mvc.{ActionBuilder, AnyContent, ControllerComponents}
import play.api.mvc.Security.{AuthenticatedBuilder, AuthenticatedRequest}
import Auth.AuthRequest
import components.GoogleAuthComponents
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

trait AuthActions { _: GoogleAuthComponents =>

  def controllerComponents: ControllerComponents
  def wsClient: WSClient
  implicit def ec: ExecutionContext

  /**
    * Should authentication be enabled.
    * This should only be set to false when running service tests.
    * If false, authentication will always succeed, with a dummy user added to the request.
    */
  def enableAuth: Boolean

  val requestLogger = new LogRequest

  def Authenticated: ActionBuilder[AuthRequest, AnyContent] = {
    if (enableAuth)
      new AuthAction(googleAuthConfig, routes.AuthController.login(), controllerComponents.parsers.default) andThen requestLogger
    else
      DummyAuthAction
  }

  object DummyAuthAction
      extends AuthenticatedBuilder[UserIdentity](
        userinfo = _ => {
          Logger.info("Skipping authentication because auth is disabled")
          Some(
            UserIdentity(sub = "dummy.user",
                         email = "dummy.email",
                         firstName = "Dummy",
                         lastName = "User",
                         exp = Long.MaxValue,
                         avatarUrl = None))
        },
        defaultParser = controllerComponents.parsers.default
      )

//  object DummyAuthAction
//      extends AuthenticatedBuilder[UserIdentity](userinfo = _ => {
//        Logger.info("Skipping authentication because auth is disabled")
//        Some(
//          UserIdentity(sub = "dummy.user",
//                       email = "dummy.email",
//                       firstName = "Dummy",
//                       lastName = "User",
//                       exp = Long.MaxValue,
//                       avatarUrl = None))
//      })
//
//  def Authenticated: ActionBuilder[AuthRequest] =
//    if (enableAuth) {
//      AuthAction andThen LogRequest
//    } else
//      DummyAuthAction andThen LogRequest
}
