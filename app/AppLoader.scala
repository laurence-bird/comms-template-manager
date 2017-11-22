import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Application, ApplicationLoader, Logger}
import play.api.ApplicationLoader.Context

class AppLoader extends ApplicationLoader {

  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)
    val components = new AppComponents(context)

    components.application
  }

//  override def load(context: Context): Application = {
//    new LogbackLoggerConfigurator().configure(context.environment)
//
//    val runningInDockerOnDevMachine =
//      sys.env.get("ENV").contains("LOCAL") && sys.env.get("RUNNING_IN_DOCKER").contains("true")
//
//    val components = new AppComponents(context, runningInDockerOnDevMachine)
//
//    if (runningInDockerOnDevMachine || components.configuration
//          .getOptional[Boolean]("kafka.enableConsumers")
//          .getOrElse(false))
//      ??? //components.startConsumingKafkaTopics()
//    else
//      Logger.info("NOT starting the Kafka consumers")
//
//    components.application
//  }

}
