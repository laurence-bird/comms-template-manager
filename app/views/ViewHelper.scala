package views

import java.time.Instant

import com.ovoenergy.comms.templates.model.RequiredTemplateData

object ViewHelper {

  def formatDate(instant: Instant): String =
    instant.toString

  def formatOptDate(opt: Option[Instant]): String =
    opt.map(formatDate).getOrElse("(unknown)")

  def dateToLong(instant: Instant): Long = {
    instant.toEpochMilli
  }

  def buildKey(parentId: Option[String], name: String): String = {
    if (parentId.isDefined) s"${parentId.get}.$name"
    else name
  }

  def stringToHtml(parentId: Option[String], name: String): String = {
    val key = buildKey(parentId, name)
    s"""
       | <div class="form-group container">
       |        <div class="form-group col-md-3">
       |          <label for=$key class="control-label">$name</label>
       |          <div class="input-group">
       |            <input class="form-control input-sm" type="text" id=$key name=$key required />
       |          </div>
       |        </div>
       |      </div>
      """.stripMargin
  }

  def optStringToHtml(parentId: Option[String], name: String): String = {
    val key = buildKey(parentId, name)
    s"""
       | <div class="form-group container">
       |        <div class="form-group col-md-3">
       |          <label for=$key class="control-label">$name</label>
       |          <div class="input-group">
       |            <input class="form-control input-sm" type="text" id=$key name=$key required />
       |            <span class="input-group-addon"> <input type="checkbox" id=${key}Controller onchange="manageOptionalField('${key}Controller', '$key')"} checked/>&nbsp;optional</span>
       |          </div>
       |        </div>
       |      </div>
      """.stripMargin
  }

  def stringsToHtml(parentId: Option[String], name: String): String = {
    val key = buildKey(parentId, name)
    s"""
       |  <div class="form-group container">
       |    <div>
       |      <label for="$key" class="col-md-12 control-label">$name</label>
       |    </div>
       |    <div class="form-group col-md-2" id="$key">
       |      <input class="form-control input-sm" type="text" id="$key[0]" name="$key[0]" required/>
       |      <input class="form-control input-sm" type="text" id="$key[1]" name="$key[1]" required/>
       |      <input class="form-control input-sm" type="text" id="$key[2]" name="$key[2]" required/>
       |    </div>
       |  </div>
       |  <div class="form-group col-md-12">
       |    <button type="button" id="addTo$key" class="btn btn-toolbar" onclick="addElement('$key', 'addTo$key')">Add to $name</button>&nbsp;
       |    <button type="button" id="removeFrom$key" class="btn btn-toolbar" onclick="removeElement('$key', 'removeFrom$key')">Remove from $name</button>
       |  </div>
     """.stripMargin
  }
  
  def objToHtml(parentId: Option[String], name: String, fields: RequiredTemplateData.Fields): String = {
    val key = buildKey(parentId, name)
    s"""
      | <div class="form-group container">
      |     <div>
      |         <label for="$key" class="col-md-12 control-label">$name</label>
      |     </div>
      |     <div class="bordered-form-group col-md-12" id="$key">
      |         ${recurseRequiredTemplateData(Some(key), fields)}
      |     </div>
      | </div>
    """.stripMargin
  }
  
  def optObjToHtml(parentId: Option[String], name: String, fields: RequiredTemplateData.Fields): String = {
    val key = buildKey(parentId, name)
    s"""
      |  <div class="form-group container">
      |      <div>
      |          <label for="$key" class="col-md-12 control-label">$name</label>
      |          <span class="input-group-addon"> <input type="checkbox" id="${key}Controller" onchange="manageOptionalField('${key}Controller', '$key')" checked/>&nbsp;optional</span>
      |      </div>
      |      <div class="bordered-form-group col-md-12" id="$key">
      |         ${recurseRequiredTemplateData(Some(key), fields)}
      |      </div>
      |  </div>
    """.stripMargin
  }

  def objsToHtml(parentId: Option[String], name: String, fields: RequiredTemplateData.Fields): String = {
    val key = buildKey(parentId, name)
    s"""
       |   <div class="form-group container">
       |       <div>
       |           <label for="$key" class="col-md-12 control-label">$name</label>
       |       </div>
       |       <div class="bordered-form-group col-md-12" id="$key">
       |           <div class="bordered-form-group col-md-12" id="$key[0]">
       |             ${recurseRequiredTemplateData(Some(key+"[0]"), fields)}
       |           </div>
       |           <div class="bordered-form-group col-md-12" id="$key[1]">
       |             ${recurseRequiredTemplateData(Some(key+"[1]"), fields)}
       |           </div>
       |       </div>
       |   </div>
       |   <div class="form-group col-md-12">
       |       <button type="button" id="add$key" class="btn btn-toolbar" onclick="addElement('$key', 'add$key')">Add to $name</button>&nbsp;
       |       <button type="button" id="remove$key" class="btn btn-toolbar" onclick="removeElement('$key', 'remove$key')">Remove from $name</button>
       |   </div>
    """.stripMargin
  }

  def recurseRequiredTemplateData(parentId: Option[String], fields: RequiredTemplateData.Fields): String = {
    fields.foldLeft(""){case (html, (key, data)) =>
      data match {
        case RequiredTemplateData.string    => html + stringToHtml(parentId, key)
        case RequiredTemplateData.optString => html + optStringToHtml(parentId, key)
        case RequiredTemplateData.strings   => html + stringsToHtml(parentId, key)
        case RequiredTemplateData.obj(f)    => html + objToHtml(parentId, key, f)
        case RequiredTemplateData.optObj(f) => html + optObjToHtml(parentId, key, f)
        case RequiredTemplateData.objs(f)   => html + objsToHtml(parentId, key, f)
      }
    }
  }

}
