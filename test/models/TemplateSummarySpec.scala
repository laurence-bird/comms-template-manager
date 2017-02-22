package models

import org.scalatest.{FlatSpec, Matchers}

class TemplateSummarySpec extends FlatSpec with Matchers {

  behavior of "TemplateSummary"

  it should "compare versions correctly" in {
    TemplateSummary.versionCompare("1.12", "1.3") shouldBe 1
    TemplateSummary.versionCompare("1.2", "1.12") shouldBe -1
    TemplateSummary.versionCompare("1.12.1", "1.12") shouldBe 1
    TemplateSummary.versionCompare("1.12", "1.12.2") shouldBe -1
    TemplateSummary.versionCompare("1.12", "1.3.1") shouldBe 1
    TemplateSummary.versionCompare("1.2", "1.1.1") shouldBe 1
    TemplateSummary.versionCompare("1.0", "1.0.1") shouldBe -1
  }

}
