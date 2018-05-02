import aws.dynamo.Dynamo
import aws.s3.AmazonS3ClientWrapper
import aws.{AwsContextProvider, Interpreter}
import com.amazonaws.regions.Regions
import com.gu.scanamo.Table
import com.ovoenergy.comms.templates.s3.{AmazonS3ClientWrapper => TemplatesLibS3ClientWrapper}
import components.Retry.RetryConfig
import components.{ConfigUtil, GoogleAuthComponents, Retry}
import controllers._
import models.{TemplateSummary, TemplateVersion}
import pagerduty.PagerDutyAlerter
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponentsFromContext, Logger}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import preview.ComposerClient
import aws.dynamo.DynamoFormats._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import router.Routes

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with GoogleAuthComponents
    with ConfigUtil
    with AssetsComponents
    with AuthActions {

  implicit val actorSys = actorSystem

  val region                   = configuration.getString("aws.region").map(Regions.fromName).getOrElse(Regions.EU_WEST_1)
  val isRunningInCompose       = sys.env.get("DOCKER_COMPOSE").contains("true")
  val (dynamoClient, s3Client) = AwsContextProvider.genContext(isRunningInCompose, region)

  val dynamo = new Dynamo(
    dynamoClient,
    Table[TemplateVersion](mandatoryConfig("aws.dynamo.tables.templateVersionTable")),
    Table[TemplateSummary](mandatoryConfig("aws.dynamo.tables.templateSummaryTable"))
  )

  val awsContext = aws.Context(
    templatesS3ClientWrapper = new TemplatesLibS3ClientWrapper(s3Client),
    s3ClientWrapper = new AmazonS3ClientWrapper(s3Client),
    dynamo = dynamo,
    s3RawTemplatesBucket = mandatoryConfig("aws.s3.buckets.rawTemplates"),
    s3TemplateFilesBucket = mandatoryConfig("aws.s3.buckets.templates"),
    s3TemplateAssetsBucket = mandatoryConfig("aws.s3.buckets.assets"),
    region = region
  )

  lazy val enableAuth = {
    !(sys.env.get("DISABLE_AUTH").contains("true") | isRunningInCompose)
  }

  Logger.info(s"Auth enabled? ${enableAuth}")

  val pagerdutyCtxt = PagerDutyAlerter.Context(
    url = mandatoryConfig("pagerduty.url"),
    serviceKey = mandatoryConfig("pagerduty.apiKey"),
    enableAlerts = configuration.getBoolean("pagerduty.alertsEnabled").getOrElse(true)
  )
  val interpreter = Interpreter.build(awsContext, pagerdutyCtxt)

  val commPerformanceUrl = mandatoryConfig("auditLog.commPerformanceUrl")
  val commSearchUrl      = mandatoryConfig("auditLog.commSearchUrl")
  val libraroMetricsUrl  = mandatoryConfig("librato.metricsUrl")

  val composerClient = new ComposerClient(wsClient, mandatoryConfig("composer.http.endpoint"))

  val mainController = new MainController(
    Authenticated,
    controllerComponents,
    interpreter,
    commPerformanceUrl,
    commSearchUrl,
    libraroMetricsUrl,
    awsContext,
    s3Client,
    composerClient
  )

  val authController = new AuthController(googleAuthConfig, wsClient, groupsAuthConfig, controllerComponents)

  lazy val router: Router = new Routes(
    httpErrorHandler,
    mainController,
    assets,
    authController
  )

  override def httpFilters: Seq[EssentialFilter] = List.empty

  override implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
}
