package preview

import com.ovoenergy.comms.model.TemplateData
import com.ovoenergy.comms.templates.model.RequiredTemplateData

trait TemplateDataGenerator {

  def generateTemplateData(requiredTemplateData: RequiredTemplateData): Option[TemplateData] =
    requiredTemplateData match {
      case s @ RequiredTemplateData.string =>
        Some(TemplateData.fromString(s.description))

      case RequiredTemplateData.strings =>
        Some(
          TemplateData.fromSeq(
            Seq(TemplateData.fromString("First element"), TemplateData.fromString("Second element"))))

      case RequiredTemplateData.obj(fields) =>
        Some(TemplateData.fromMap(fields.mapValues(generateTemplateData).collect {
          case (key, Some(value)) => key -> value
        }))

      case RequiredTemplateData.objs(fields) =>
        val obj = TemplateData.fromMap(fields.mapValues(generateTemplateData).collect {
          case (key, Some(value)) => key -> value
        })
        Some(TemplateData.fromSeq(Seq(obj, obj)))

      case RequiredTemplateData.optObj(_) =>
        None

      case RequiredTemplateData.optString =>
        None

    }

}

object TemplateDataGenerator extends TemplateDataGenerator
