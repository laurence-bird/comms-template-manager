package controllers

import org.slf4j.MDC
import play.api.Logger
import play.api.mvc.ActionTransformer
import Auth.AuthRequest

import scala.concurrent.Future

object LogRequest extends ActionTransformer[AuthRequest, AuthRequest] {

  private val requestLogger = Logger("requestLogger")

  protected def transform[A](request: AuthRequest[A]) = {
    withMDC(request.user.email)(
      requestLogger.info(s"User Name = ${request.user.fullName}, request = ${request.method} ${request.uri}"))
    Future.successful(request)
  }

  private val UserEmail = "userEmail"

  private def withMDC(userEmail: String)(block: => Unit): Unit = {
    MDC.put(UserEmail, userEmail)
    try {
      block
    } finally {
      MDC.remove(UserEmail)
    }
  }
}
