package com.songo.scalar

/**
 * Created by lukaszsamson on 07.03.15.
 */

import spray.json.{RootJsonFormat, DefaultJsonProtocol}

import scala.language.experimental.macros


trait AutoProductFormat extends DefaultJsonProtocol {
  implicit def jsonFormat[T <: Product]: RootJsonFormat[T] = macro AutoProductFormatMacro.autoProductFormatMacro[T]
}

object AutoProductFormat extends AutoProductFormat


object AutoProductFormatMacro {
  import scala.reflect.macros.Context

  def autoProductFormatMacro[T: c.WeakTypeTag](c: Context): c.Expr[RootJsonFormat[T]] = {
    import c.universe._

    val tt = weakTypeTag[T]
    val ts = tt.tpe.typeSymbol.asClass

    val args = tt.tpe.declarations
      .collect { case m: MethodSymbol if m.isCaseAccessor => m.name -> m.returnType }
      .toList
    val argNames = args.map { case (n, _) => Literal(Constant(n.toString)) }
    val argTypes = args.map { case (_, t) => parseType(c)(t.toString).head }

    c.Expr[RootJsonFormat[T]](
      Apply(
        TypeApply(
          Select(reify(spray.json.DefaultJsonProtocol).tree, newTermName("jsonFormat")),
          argTypes :+ Ident(ts)
        ),
        Select(Ident(ts.companionSymbol), newTermName("apply")) :: argNames
      )
    )
  }

  protected def parseType(c: Context)(tpe: String): List[c.Tree] = {
    import c.universe._

    def resolveType(qualifiedName: String): Tree =
      qualifiedName.trim().split('.').toList match {
        case x :: Nil => Ident(newTypeName(x))
        case xs :+ x =>
          val parts = xs.map(newTermName(_))
          val pkg: Tree = parts.tail.foldLeft[Tree] (Ident(parts.head)) { (tree, part) => Select(tree, part) }
          Select(pkg, newTypeName(x))
      }

    val x = tpe.indexOf("[")
    if (x > 0) {
      val root = tpe.substring(0, x)
      val parameters = tpe.substring(x + 1, tpe.lastIndexOf("]"))
      List(
        AppliedTypeTree(
          Ident(newTypeName(root)),
          parseType(c)(parameters)
        )
      )
    } else {
      tpe.split(',').toList.map(resolveType(_))
    }
  }

}