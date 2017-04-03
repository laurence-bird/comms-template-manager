package controllers

import com.gu.googleauth.{Actions, UserIdentity}
import play.api.Logger
import play.api.mvc.ActionBuilder
import play.api.mvc.Security.{AuthenticatedBuilder, AuthenticatedRequest}
import Auth.AuthRequest

trait AuthActions extends Actions {
  override val loginTarget           = routes.AuthController.login()
  override val defaultRedirectTarget = routes.MainController.index()
  override val failureRedirectTarget = routes.AuthController.authError()

  /**
    * Should authentication be enabled.
    * This should only be set to false when running service tests.
    * If false, authentication will always succeed, with a dummy user added to the request.
    */
  def enableAuth: Boolean

  object DummyAuthAction
      extends AuthenticatedBuilder[UserIdentity](userinfo = _ => {
        Logger.info("Skipping authentication because auth is disabled")
        Some(
          UserIdentity(sub = "dummy.user",
                       email = "dummy.email",
                       firstName = "Dummy",
                       lastName = "User",
                       exp = Long.MaxValue,
                       avatarUrl = None))
      })

  def Authenticated: ActionBuilder[AuthRequest] =
    if (enableAuth) {
      AuthAction andThen LogRequest
    } else
      DummyAuthAction andThen LogRequest
}
