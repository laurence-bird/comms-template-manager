package controllers

import com.gu.googleauth.GoogleAuthConfig
import play.api.libs.ws.WSClient
import play.api.mvc._

class MainController(val authConfig: GoogleAuthConfig, val wsClient: WSClient, val enableAuth: Boolean) extends AuthActions with Controller {

  val healthcheck = Action { Ok("OK") }

  val index = Authenticated { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

}
