package components

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import com.gu.googleauth._
import controllers.GroupsAuthConfig
import play.api.Logger

import scala.collection.JavaConverters._

trait GoogleAuthComponents { self: ConfigUtil =>

  def googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domain = "ovoenergy.com"
  )

  def groupsAuthConfig: Option[GroupsAuthConfig] = {
    if (configuration.getOptional[Boolean]("google.groupsAuthorisation.enabled").getOrElse(false)) {
      val acceptedGroups = configuration
        .getStringList("google.groupsAuthorisation.acceptedGroups")
        .map(_.asScala.toSet)
        .getOrElse(Set.empty)

      val serviceAccount = {
        import com.google.api.client.googleapis.auth.oauth2.GoogleCredential

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
