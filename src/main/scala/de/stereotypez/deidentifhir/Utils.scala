package de.stereotypez.deidentifhir

import org.hl7.fhir.r4.model.{Base, PrimitiveType, Property, Type}
import org.reflections.Reflections

import java.lang.reflect.{Field, Method}
import javax.lang.model.SourceVersion
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.CollectionHasAsScala

case class FhirProperty(property: Property, field: Field)

object DeidentifhirUtils {

  def nameToField(name: String): String = {
    name match {
      case s"$n[x]" => n
      case n if SourceVersion.isKeyword(n) => n + "_"
      case n => n
    }
  }

  @tailrec
  def getAccessibleField(clazz: Class[_], fieldName: String): Field =
    try {
      val field = clazz.getDeclaredField(nameToField(fieldName))
      field.setAccessible(true)
      field
    }
    catch {
      case e: NoSuchFieldException =>
        val superClass = clazz.getSuperclass
        if (superClass == null) throw e
        else getAccessibleField(superClass, fieldName);
    }

  private val types = new Reflections("org.hl7.fhir.r4.model").getSubTypesOf(classOf[PrimitiveType[_]]).asScala.toSeq
    .map(_.getSimpleName.toLowerCase)
    .sorted

  def hasTypeEnding(typeName: String): Boolean = {
    types.contains(s"${typeName}type".toLowerCase)
  }

  def extractTypes(property: Property): Seq[String] = {
    (property.getTypeCode match {
      case typeCode if typeCode.startsWith("canonical(") =>
        val typeMatcher = "\\(([^\\)]+)\\)".r
        typeMatcher.findAllIn(typeCode).matchData
          .flatMap(_.group(1).split("\\|").toSeq).toSeq
      case typeCode =>
        typeCode.replaceAll("\\([^)]*\\)", "").split("\\|").toSeq
    })
      .map(_.trim)
      .filter(_.nonEmpty)
      //.filterNot(t => Try(Class.forName(s"org.hl7.fhir.r4.model.$t")).isFailure)
  }

  def getChildren(r: Base): Seq[FhirProperty] = {
    r.children().asScala.toSeq
      .map { p => FhirProperty(p, getAccessibleField(r.getClass, nameToField(p.getName)))}
  }

  def getChildrenWithValue(r: Base): Map[FhirProperty, Any] = {
    getChildren(r)
      .filter(_.field.get(r) != null)
      .map(fp => fp -> fp.field.get(r))
      .toMap
  }

  def fhirCircuitBreak(element: String, path: Seq[String]): Boolean = {
    element match {
      case "identifier" if path.endsWith(Seq("identifier", "assigner")) => true
      case "extension" if path.endsWith(Seq("extension")) => true
      case "provision" if path.endsWith(Seq("provision")) => true
      case "rule" if path.endsWith(Seq("rule")) => true
      case "contains" if path.endsWith(Seq("contains")) => true
      case "page" if path.endsWith(Seq("page")) => true
      case "instantiates" if path.endsWith(Seq("instantiates")) => true
      case "imports" if path.endsWith(Seq("imports")) => true
      case "import" if path.endsWith(Seq("import")) => true
      case "partOf" if path.endsWith(Seq("partOf")) => true
      case "partOf" if path.endsWith(Seq("partOf", "replaces")) => true
      case "derivedFrom" if path.endsWith(Seq("derivedFrom")) => true
      case "replaces" if path.endsWith(Seq("replaces")) => true
      case "instantiates" if path.endsWith(Seq("instantiates", "imports")) => true
      case "workflow" if path.endsWith(Seq("workflow")) => true
      case "id" if path.endsWith(Seq("id")) => true
      case "id" if path.endsWith(Seq("id", "extension")) => true
      case "url" if path.endsWith(Seq("id", "extension")) => true
      case "url" if path.endsWith(Seq("meta", "extension")) => true
      case "extension" if path.endsWith(Seq("extension", "url")) => true
      case "concept" if path.endsWith(Seq("concept")) => true
      case "section" if path.endsWith(Seq("section")) => true
      case "process" if path.endsWith(Seq("process", "step")) => true
      case "step" if path.endsWith(Seq("process","step","alternative")) => true
      case "packageItem" if path.endsWith(Seq("packageItem")) => true
      case "link" if path.endsWith(Seq("link", "target")) => true
      case "part" if path.endsWith(Seq("parameter", "part")) => true
      case "item" if path.endsWith(Seq("item")) => true
      case "synonym" if path.endsWith(Seq("synonym")) => true
      case "synonym" if path.endsWith(Seq("synonym", "translation")) => true
      case "translation" if path.endsWith(Seq("translation")) => true
      case "group" if path.endsWith(Seq("group")) => true
      case "application" if path.endsWith(Seq("application")) => true
      case "action" if path.endsWith(Seq("action")) => true
      case "item" if path.endsWith(Seq("item", "answer")) => true
      case _ => false
    }
  }
}
