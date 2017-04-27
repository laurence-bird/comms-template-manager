import aws.{AwsContextProvider, Interpreter}
import aws.dynamo.Dynamo
import aws.s3.AmazonS3ClientWrapper
import com.amazonaws.regions.Regions
import com.gu.googleauth.GoogleAuthConfig
import com.gu.scanamo.Table
import controllers.{Assets, AssetsBuilder, AuthController, MainController}
import models.{TemplateSummary, TemplateVersion}
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import router.Routes
import aws.dynamo.DynamoFormats._
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, MessagesApi}
import com.ovoenergy.comms.templates.s3.{AmazonS3ClientWrapper => TemplatesLibS3ClientWrapper}
import pagerduty.PagerDutyAlerter

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) with AhcWSComponents {

  implicit val actorSys = actorSystem

  def mandatoryConfig(key: String): String =
    configuration.getString(key).getOrElse(sys.error(s"Missing config key: $key"))

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

  val googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domain = "ovoenergy.com"
  )
  val enableAuth = !isRunningInCompose // only disable auth if we are running the service tests

  val pagerdutyCtxt = PagerDutyAlerter.Context(
    url = mandatoryConfig("pagerduty.url"),
    serviceKey = mandatoryConfig("pagerduty.apiKey"),
    enableAlerts = configuration.getBoolean("pagerduty.alertsEnabled").getOrElse(true)
  )
  val interpreter              = Interpreter.build(awsContext, pagerdutyCtxt)
  val messagesApi: MessagesApi = new DefaultMessagesApi(environment, configuration, new DefaultLangs(configuration))

  val commPerformanceUrl = mandatoryConfig("auditLog.commPerformanceUrl")
  val commSearchUrl      = mandatoryConfig("auditLog.commSearchUrl")
  val libraroMetricsUrl  = mandatoryConfig("librato.metricsUrl")

  val mainController = new MainController(googleAuthConfig,
                                          wsClient,
                                          enableAuth,
                                          interpreter,
                                          messagesApi,
                                          commPerformanceUrl,
                                          commSearchUrl,
                                          libraroMetricsUrl)

  val authController = new AuthController(googleAuthConfig, wsClient, enableAuth)

  lazy val router: Router = new Routes(
    httpErrorHandler,
    mainController,
    new Assets(httpErrorHandler),
    authController
  )

}
