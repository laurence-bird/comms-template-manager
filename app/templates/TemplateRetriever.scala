package templates

import cats.data.Validated.{Invalid, Valid}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, ContainerCredentialsProvider}
import com.ovoenergy.comms.model.CommManifest
import com.ovoenergy.comms.templates.{TemplatesContext, TemplatesRepo}
import com.ovoenergy.comms.templates.model.RequiredTemplateData

object TemplateRetriever {

  private val awsCreds = new AWSCredentialsProviderChain(
    new ContainerCredentialsProvider(),
    new ProfileCredentialsProvider()
  )

  def getTemplateRequiredData(commManifest: CommManifest): TemplateErrors[RequiredTemplateData.obj] = {
    val templatesContext = TemplatesContext.nonCachingContext(awsCreds)
    val validationResult = for {
      template <- TemplatesRepo.getTemplate(templatesContext, commManifest).toEither.right
      result   <- template.combineRequiredData.toEither.right
    } yield result

    validationResult match {
      case Right(requiredData) => Valid(requiredData)
      case Left(error)         => Invalid(error)
    }
  }
}
