import aws.{AwsContextProvider, Interpreter}
import aws.dynamo.Dynamo
import aws.s3.AmazonS3ClientWrapper
import controllers.{AuthController, MainController}
import com.amazonaws.regions.Regions
import com.gu.googleauth.GoogleAuthConfig
import com.gu.scanamo.Table
import models.{TemplateSummary, TemplateVersion}
import play.api.ApplicationLoader.Context
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logger}
import play.api.libs.ws.ahc.AhcWSComponents
import router.Routes
import aws.dynamo.DynamoFormats._
class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents {

  implicit val actorSys = actorSystem

  def mandatoryConfig(key: String): String = configuration.getString(key).getOrElse(sys.error(s"Missing config key: $key"))

  val region = configuration.getString("aws.region").map(Regions.fromName).getOrElse(Regions.EU_WEST_1)
  val isRunningInCompose = sys.env.get("DOCKER_COMPOSE").contains("true")
  val (dynamoClient, s3Client) = AwsContextProvider.genContext(isRunningInCompose, region)

  val dynamo = new Dynamo(
    dynamoClient,
    Table[TemplateVersion](mandatoryConfig("aws.dynamo.tables.templateVersionTable")),
    Table[TemplateSummary](mandatoryConfig("aws.dynamo.tables.templateSummaryTable"))
  )

  val awsContext = aws.Context(new AmazonS3ClientWrapper(s3Client), dynamo, mandatoryConfig("aws.s3.buckets.rawTemplates"))

  val googleAuthConfig = GoogleAuthConfig(
    clientId = mandatoryConfig("google.clientId"),
    clientSecret = mandatoryConfig("google.clientSecret"),
    redirectUrl = mandatoryConfig("google.redirectUrl"),
    domain = "ovoenergy.com"
  )
  val enableAuth = !isRunningInCompose // only disable auth if we are running the service tests

  val interpreter = Interpreter.build(awsContext)

  val mainController = new MainController(googleAuthConfig, wsClient, enableAuth, interpreter)
  val authController = new AuthController(googleAuthConfig, wsClient, enableAuth)

  lazy val router: Router = new Routes(
    httpErrorHandler,
    mainController,
    authController
  )

}
