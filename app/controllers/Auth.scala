package controllers

import com.gu.googleauth.UserIdentity
import play.api.mvc.Security.AuthenticatedRequest

object Auth {

  type AuthRequest[A] = AuthenticatedRequest[A, UserIdentity]

}
