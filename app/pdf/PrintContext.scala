package pdf

import com.ovoenergy.comms.templates.TemplatesContext
import components.Retry.RetryConfig
import components.S3PdfRepo.S3Config
import okhttp3.{Request, Response}

import scala.util.Try

case class PrintContext(docRaptorConfig: DocRaptorConfig,
                        s3Config: S3Config,
                        retryConfig: RetryConfig,
                        templateContext: TemplatesContext,
                        httpClient: Request => Try[Response])
