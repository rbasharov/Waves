package com.wavesplatform.lang.v1.evaluator.ctx

import cats.data.EitherT
import com.wavesplatform.lang.TrampolinedExecResult
import com.wavesplatform.lang.v1.compiler.Terms.TYPE
import monix.eval.Coeval

sealed trait LazyVal {
  val tpe: TYPE
  val value: TrampolinedExecResult[tpe.Underlying]

  override def toString: String = {
    s"Type: ${tpe.typeInfo}, Value: ${value.value
      .attempt()
      .fold(
        err => s"Failure($err)",
        _.fold(
          err => s"Failure($err)",
          v => s"Success($v)"
        )
      )}"
  }
}

object LazyVal {
  private case class LazyValImpl(tpe: TYPE, v: TrampolinedExecResult[Any]) extends LazyVal {
    override val value: TrampolinedExecResult[tpe.Underlying] = EitherT(Coeval.evalOnce(v.map(_.asInstanceOf[tpe.Underlying]).value.apply()))
  }

  def apply(t: TYPE)(v: TrampolinedExecResult[t.Underlying]): LazyVal = LazyValImpl(t, v.map(_.asInstanceOf[Any]))
}
