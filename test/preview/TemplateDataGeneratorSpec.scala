package preview

import com.ovoenergy.comms.model.TemplateData
import com.ovoenergy.comms.templates.model.RequiredTemplateData
import org.scalatest.{FlatSpec, Matchers}

class TemplateDataGeneratorSpec extends FlatSpec with Matchers with TemplateDataGenerator {

  "TemplateDataGenerator" should "generate a None when RequiredTemplateData is optString" in {
    generateTemplateData(RequiredTemplateData.optString) shouldBe None
  }

  "TemplateDataGenerator" should "generate a None when RequiredTemplateData is optObj" in {
    generateTemplateData(RequiredTemplateData.optObj(Map("key1" -> RequiredTemplateData.string))) shouldBe None
  }

  "TemplateDataGenerator" should "generate a TemplateData with String when RequiredTemplateData is string" in {
    generateTemplateData(RequiredTemplateData.string) shouldBe Some(
      TemplateData.fromString(RequiredTemplateData.string.description))
  }

  "TemplateDataGenerator" should "generate a TemplateData with sequence of String when RequiredTemplateData is strings" in {
    generateTemplateData(RequiredTemplateData.strings) shouldBe Some(
      TemplateData.fromSeq(
        Seq(
          TemplateData.fromString("First element"),
          TemplateData.fromString("Second element")
        )))
  }
}
