package sttp.tapir.json.upickle.auto

import scala.deriving.Mirror
import scala.quoted.Expr
import scala.quoted.Quotes
import scala.quoted.Type
import scala.reflect.ClassTag
import sttp.tapir.Schema
import scala.quoted.*
import upickle.default

inline def addAnnotation[T](using m: Mirror.Of[T], ct: ClassTag[T]): default.ReadWriter[T] = inline m match {
  case m: Mirror.ProductOf[T] =>
    addAnnotationProduct[T, m.MirroredElemTypes](ct) 
  case _ => null // TODO
}

inline def addAnnotationProduct[T, Elems <: Tuple](inline ct: ClassTag[T]) =
  ${ addAnnotationImpl[T, Elems]('ct)}

def addAnnotationImpl[T: Type, Elems <: Tuple](ct: Expr[ClassTag[T]])(using Quotes, Type[Elems]): Expr[default.ReadWriter[T]] = {
  import quotes.reflect.*
  import quoted.*
  val caseClassTree: TypeTree = TypeTree.of[T]

  // iterate over fields of T and build a list of refinements
  val fieldTrees: List[Definition] = TypeRepr.of[T].typeSymbol.primaryConstructor.paramSymss.flatten.map { field =>
    field.tree.asInstanceOf[Definition] // TODO rewrite without asInstanceOf, pattern matching perhaps?
  }



  // println(s">>>>>>>>>>>>>>>>> ${mirror.tpe.asTerm.symbol}")

  // https://usesynchronizedrs.scala-lang.org/t/how-to-refine-type-dynamically-in-scala-3-whitebox-macro/9220
  // // problem: even if we create a new type out of T by enriching its annotations, the newly created type T2 has no Mirror.Of[]
  val refinedTypeTree: TypeTree = Refined.copy(caseClassTree)(TypeTree.of[T], fieldTrees)
  println(refinedTypeTree.show(using Printer.TreeShortCode))
  println(caseClassTree.show(using Printer.TreeShortCode))

  // TODO just experimentally using caseClassTree, should be refinedTypeTree 
  //val tpe: TypeRepr = refinedTypeTree.tpe
  val tpe: TypeRepr = caseClassTree.tpe
  

  val tpeSymbol = TypeRepr.of[T].typeSymbol.name
  val tpeSymbolExpr = Expr(tpeSymbol)
  val readWriterExpr = tpe.asType match {
    case '[t] =>
      '{
        val tpeSymbolName: String = ${tpeSymbolExpr}
        given ClassTag[t] = $ct.asInstanceOf[ClassTag[t]]
        given mirrorRefined: Mirror.ProductOf[t] = new Mirror.Product { // TODO support sum and singleton
          type MirroredType = t
          type MirroredMonoType = t
          type MirroredElemTypes = Elems 
          override def fromProduct(p: scala.Product): t = throw new IllegalStateException(s"Unexpected call to fromProduct on type t copied from $tpeSymbolName") // TODO 
        }
        println(s"Derived macroRW for $tpeSymbolName")
        val readWriter: default.ReadWriter[t] = upickle.default.macroRW[t] // TODO not default
        readWriter.asInstanceOf[default.ReadWriter[T]]
      }
  }
  println(readWriterExpr.show)
  readWriterExpr
}

/** Builds serialization configuration for a specific case class T. This macro merges the given global configuration with information read
  * from class annotations like @encodedName and others.
  *
  * @param config
  */
inline def caseClassConfiguration[T: ClassTag](config: CodecConfiguration)(using Mirror.Of[T]): ClassCodecConfiguration = ${
  caseClassConfigurationImpl[T]('config)
}

def caseClassConfigurationImpl[T: Type](config: Expr[CodecConfiguration])(using Quotes): Expr[ClassCodecConfiguration] =
  import quotes.reflect.*

  val tpe = TypeRepr.of[T]
  println(">>>>")
  println(tpe.typeSymbol)
  println("<<<<<")
  // TODO add upickle annotations to fields already annotated by Tapir annotations
  val tpeWithParamAnnotation = Refinement(tpe, "addedField", TypeRepr.of[String])
  val paramAnns = tpeWithParamAnnotation.asType match
    case '[t] =>
      new CollectAnnotations[t].paramAnns

  // construct encoded names
  // scan all fields of type T
  println(paramAnns)

  val printableAnns: List[Expr[Unit]] = paramAnns.toMap.view.mapValues(Expr.ofList).toMap.toList.flatMap { case (field, exprOfAnnList) =>
    val fieldExpr = Expr(field)
    List(
      '{ println($fieldExpr) },
      '{ println($exprOfAnnList) }
    )
  }

  val encodedNameAnns: List[Option[Term]] = paramAnns.map { case (paramName, annExprs) =>
    annExprs
      .map { case annExpr: Expr[Any] =>
        annExpr.asTerm
      }
      .find(_.tpe =:= TypeRepr.of[Schema.annotations.encodedName])
  }

  val printableEncodedNameAnns: Expr[List[Any]] = Expr.ofList(encodedNameAnns.map(_.map(_.asExprOf[Any])).flatMap(_.toList))

  Expr.block(
    printableAnns :+ '{ println($printableEncodedNameAnns) },
    '{ new ClassCodecConfiguration($config, Map.empty, Map.empty) } // TODO Map.empty
  )

class CollectAnnotations[T: Type](using val quotes: Quotes) { // Copied from Magnolia and modified
  import quotes.reflect.*

  private val tpe: TypeRepr = TypeRepr.of[T]

  def anns: Expr[List[Any]] =
    Expr.ofList {
      tpe.typeSymbol.annotations
        .filter(filterAnnotation)
        .map(_.asExpr.asInstanceOf[Expr[Any]])
    }

  def inheritedAnns: Expr[List[Any]] =
    Expr.ofList {
      tpe.baseClasses
        .filterNot(isObjectOrScala)
        .collect {
          case s if s != tpe.typeSymbol => s.annotations
        } // skip self
        .flatten
        .filter(filterAnnotation)
        .map(_.asExpr.asInstanceOf[Expr[Any]])
    }

  def typeAnns: Expr[List[Any]] = {

    def getAnnotations(t: TypeRepr): List[Term] = t match
      case AnnotatedType(inner, ann) => ann :: getAnnotations(inner)
      case _                         => Nil

    val symbol: Option[Symbol] =
      if tpe.typeSymbol.isNoSymbol then None else Some(tpe.typeSymbol)
    Expr.ofList {
      symbol.toList.map(_.tree).flatMap {
        case ClassDef(_, _, parents, _, _) =>
          parents
            .collect { case t: TypeTree => t.tpe }
            .flatMap(getAnnotations)
            .filter(filterAnnotation)
            .map(_.asExpr.asInstanceOf[Expr[Any]])
        case _ =>
          List.empty
      }
    }
  }

  def paramAnns: List[(String, List[Expr[Any]])] =
    groupByParamName {
      (fromConstructor(tpe.typeSymbol) ++ fromDeclarations(tpe.typeSymbol))
        .filter { case (_, anns) => anns.nonEmpty }
    }

  def inheritedParamAnns: List[(String, List[Expr[Any]])] =
    groupByParamName {
      tpe.baseClasses
        .filterNot(isObjectOrScala)
        .collect {
          case s if s != tpe.typeSymbol =>
            (fromConstructor(s) ++ fromDeclarations(s)).filter { case (_, anns) =>
              anns.nonEmpty
            }
        }
        .flatten
    }

  private def fromConstructor(from: Symbol): List[(String, List[Expr[Any]])] =
    println(s"Extracting constructor anns from $from")
    from.primaryConstructor.paramSymss.flatten.map { field =>
      println("Field:")
      println(field.name)
      field.name -> field.annotations
        .map(ann =>
          println(ann)
          ann
        )
        .filter(filterAnnotation)
        .map(_.asExpr.asInstanceOf[Expr[Any]])
    }

  private def fromDeclarations(
      from: Symbol
  ): List[(String, List[Expr[Any]])] =
    from.declarations.collect {
      case field: Symbol if (field.tree: @unchecked).isInstanceOf[ValDef] =>
        field.name -> field.annotations
          .filter(filterAnnotation)
          .map(_.asExpr.asInstanceOf[Expr[Any]])
    }

  private def groupByParamName(anns: List[(String, List[Expr[Any]])]): List[(String, List[Expr[Any]])] =
    anns
      .groupBy { case (name, _) => name }
      .toList
      .map { case (name, l) => name -> l.flatMap(_._2) }

  private def isObjectOrScala(bc: Symbol) =
    bc.name.contains("java.lang.Object") || bc.fullName.startsWith("scala.")

  private def filterAnnotation(a: Term): Boolean =
    a.tpe.typeSymbol.maybeOwner.isNoSymbol ||
      a.tpe.typeSymbol.owner.fullName != "scala.annotation.internal"
}
