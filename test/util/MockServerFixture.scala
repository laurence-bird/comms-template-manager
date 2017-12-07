package util

import java.util.concurrent.TimeUnit

import org.mockserver.client.server.MockServerClient
import org.scalatest.time.Span
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

import scala.concurrent.duration._

trait MockServerFixture extends BeforeAndAfterEach with BeforeAndAfterAll { _: Suite =>

  def defaultMockServerHost: String       = "127.0.0.1"
  def defaultMockServerPort: Int          = 1080
  def defaultMockServerStartTimeout: Span = 10.seconds

  private var mutableMockServerClient: Option[MockServerClient] = None
  def mockServerClient: MockServerClient =
    mutableMockServerClient.getOrElse(throw new IllegalStateException("The MockServerClient is not yet started"))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    mutableMockServerClient = Some(new MockServerClient(defaultMockServerHost, defaultMockServerPort))
  }

  override protected def afterAll(): Unit = {
    mutableMockServerClient.foreach(_.stop(true))
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    mutableMockServerClient.foreach { mockServerClient =>
      mockServerClient.isRunning(10, defaultMockServerStartTimeout.toMillis, TimeUnit.MILLISECONDS)
      mockServerClient.reset()
    }
  }

  override protected def afterEach(): Unit = {
    mockServerClient.reset()
    super.afterEach()
  }
}
