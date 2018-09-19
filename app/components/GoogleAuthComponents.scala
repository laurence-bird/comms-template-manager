package components

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.gu.googleauth._
import controllers.GroupsAuthConfig
import io.jsonwebtoken.SignatureAlgorithm
import org.joda.time.Duration
import play.api.Logger

import scala.collection.JavaConverters._

trait GoogleAuthComponents { self: ConfigUtil =>

  def googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domain = None,
    antiForgeryChecker = AntiForgeryChecker("antiForgeryToken", SignatureAlgorithm.HS256),
    maxAuthAge = Some(Duration.standardDays(1)),
    enforceValidity = true,
    prompt = Some("select_account") // see https://developers.google.com/identity/protocols/OpenIDConnect#authenticationuriparameters
  )

  def groupsAuthConfig: Option[GroupsAuthConfig] = {
    if (configuration.getOptional[Boolean]("google.groupsAuthorisation.enabled").getOrElse(false)) {
      val acceptedGroups = configuration.underlying
        .getStringList("google.groupsAuthorisation.acceptedGroups")
        .asScala
        .toSet

      val serviceAccount = {

        val credentials: GoogleCredential = {
          val json = mandatoryConfig("google.groupsAuthorisation.serviceAccountCreds")
          GoogleCredential.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
        }

        GoogleServiceAccount(
          email = credentials.getServiceAccountId,
          privateKey = credentials.getServiceAccountPrivateKey,
          impersonatedUser = mandatoryConfig("google.groupsAuthorisation.impersonatedUser")
        )
      }

      val groupChecker = new GoogleGroupChecker(serviceAccount)

      Logger.info("Google Groups authorisation is ENABLED")
      Some(GroupsAuthConfig(acceptedGroups, groupChecker))
    } else {
      Logger.info("Google Groups authorisation is DISABLED")
      None
    }
  }
}