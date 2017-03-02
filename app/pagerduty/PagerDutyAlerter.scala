package pagerduty

import io.circe.generic.auto._
import io.circe.syntax._
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import play.api.Logger

import scala.util.control.NonFatal

object PagerDutyAlerter {

  case class Context(url: String, serviceKey: String, enableAlerts: Boolean)

  case class AlertBody(service_key: String, event_type: String, description: String)

  val httpClient = new OkHttpClient()
  def apply(message: String, context: Context): Unit = {
    if(context.enableAlerts){
      val alertBody = AlertBody(context.serviceKey, "trigger", message).asJson.toString()
      try {
        val request = new Request.Builder()
          .url(context.url)
          .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), alertBody))
          .build()

        val response = httpClient
          .newCall(request)
          .execute()


        response.code match{
          case 200 => Logger.info(s"Raised alert to pagerduty for : $message}")
          case _   => Logger.warn(s"Failed to raise pagerduty alert for: $message")

        }
        response.close()
      } catch {
        case NonFatal(e) =>
          Logger.warn(s"Unable to create PagerDuty incident for message $message")
      }
    } else ()
  }
}