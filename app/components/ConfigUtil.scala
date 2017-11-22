package components

import play.api.Configuration

trait ConfigUtil {

  def configuration: Configuration

  def mandatoryConfig(key: String): String =
    configuration.get[String](key)

}
