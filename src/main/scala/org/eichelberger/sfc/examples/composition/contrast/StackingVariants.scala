package org.eichelberger.sfc.examples.composition.contrast

import org.eichelberger.sfc._
import org.eichelberger.sfc.SpaceFillingCurve._
import org.eichelberger.sfc.utils.Lexicographics.Lexicographic
import org.joda.time.DateTime

object BaseCurves {
  val RowMajor    = 0
  val ZOrder      = 1
  val Hilbert     = 2

  def decomposePrecision(combination: OrdinalVector, precision: Int): (Int, Int) = {
    val b = precision / (combination.size + 1)
    val a = precision - b
    (a, b)
  }

  def rawNWayCurve(combination: OrdinalVector, precisions: OrdinalNumber*): SpaceFillingCurve =
    combination.toSeq.last match {
      case 0 => RowMajorCurve(precisions:_*)
      case 1 => ZCurve(precisions:_*)
      case 2 => CompactHilbertCurve(precisions:_*)
    }
}
import BaseCurves._

// four-dimensionals...

case class FactoryXYZT(precision: Int, plys: Int) {
  /*
   *        SFC
   *       /   \
   *      SFC   t
   *     /   \
   *    SFC   z
   *   /   \
   *   x   y
   */
  def buildVerticalCurve(combination: OrdinalVector, precision: Int): ComposedCurve = {
    val (leftPrec, rightPrec) = decomposePrecision(combination, precision)

    val rightChild: Composable  = combination.size match {
      case 3 => DefaultDimensions.createNearDateTime(rightPrec)
      case 2 => DefaultDimensions.createDimension[Double]("z", 0.0, 50000.0, rightPrec)
      case 1 => DefaultDimensions.createLatitude(rightPrec)
      case _ => throw new Exception("Invalid right child specification")
    }
    val leftChild: Composable = combination.size match {
      case 3 | 2 => buildVerticalCurve(OrdinalVector(combination.toSeq.dropRight(1):_*), leftPrec)
      case 1 => DefaultDimensions.createLongitude(leftPrec)
      case _ => throw new Exception("Invalid left child specification")
    }

    val rawCurve = rawNWayCurve(combination, leftPrec, rightPrec)

    new ComposedCurve(rawCurve, Seq(leftChild, rightChild))
  }

  /*
   *        SFC
   *       /   \
   *    SFC     SFC
   *   /   \   /   \
   *   x   y   z   t
   */
  def buildMixedCurve22(combination: OrdinalVector, precision: Int, side: Int = 0): ComposedCurve = {
    val (leftPrec, rightPrec) = decomposePrecision(combination, precision)

    val leftChild: Composable = side match {
      case 0 => buildMixedCurve22(OrdinalVector(combination(0)), leftPrec, 1)
      case 1 => DefaultDimensions.createLongitude(leftPrec)
      case 2 => DefaultDimensions.createDimension[Double]("z", 0.0, 50000.0, leftPrec)
    }
    val rightChild: Composable = side match {
      case 0 => buildMixedCurve22(OrdinalVector(combination(1)), rightPrec, 2)
      case 1 => DefaultDimensions.createLatitude(rightPrec)
      case 2 => DefaultDimensions.createNearDateTime(rightPrec)
    }

    val rawCurve = rawNWayCurve(combination, leftPrec, rightPrec)

    new ComposedCurve(rawCurve, Seq(leftChild, rightChild))
  }

  /*
   *      SFC
   *     /   \
   *    SFC   t
   *   / | \
   *   x y z
   */
  def buildMixedCurve31(combination: OrdinalVector, precision: Int, side: Int = 0): ComposedCurve = {
    val topDimPrecision = precision >> 2
    val bottomCurvePrecision = precision - topDimPrecision
    val leafPrecisionRight = bottomCurvePrecision / 3
    val leafPrecisionCenter = (bottomCurvePrecision - leafPrecisionRight) >> 1
    val leafPrecisionLeft = bottomCurvePrecision - leafPrecisionRight - leafPrecisionCenter

    val rawCurveBottom = rawNWayCurve(OrdinalVector(combination(0)), leafPrecisionLeft, leafPrecisionCenter, leafPrecisionRight)

    val curveBottom = new ComposedCurve(
      rawCurveBottom,
      Seq(
        DefaultDimensions.createLongitude(leafPrecisionLeft),
        DefaultDimensions.createLatitude(leafPrecisionCenter),
        DefaultDimensions.createDimension[Double]("z", 0.0, 50000.0, leafPrecisionRight)
      )
    )

    val rawCurveTop = rawNWayCurve(OrdinalVector(combination(1)), bottomCurvePrecision, topDimPrecision)

    new ComposedCurve(
      rawCurveTop,
      Seq(
        curveBottom,
        DefaultDimensions.createNearDateTime(topDimPrecision)
      )
    )
  }

  /*
   *     SFC
   *   / | | \
   *   x y z t
   */
  def buildHorizontalCurve(combination: OrdinalVector, precision: Int): ComposedCurve = {
    val tPrecision = precision / 4
    val zPrecision = (precision - tPrecision) / 3
    val yPrecision = (precision - tPrecision - zPrecision) >> 1
    val xPrecision = precision - tPrecision - zPrecision - yPrecision

    val rawCurve = rawNWayCurve(combination, xPrecision, yPrecision, zPrecision, tPrecision)

    new ComposedCurve(
      rawCurve,
      Seq(
        DefaultDimensions.createLongitude(xPrecision),
        DefaultDimensions.createLatitude(yPrecision),
        DefaultDimensions.createDimension[Double]("z", 0.0, 50000.0, zPrecision),
        DefaultDimensions.createNearDateTime(tPrecision)
      )
    )
  }

  def getCurves: Seq[ComposedCurve] = {
    plys match {
      case 3 =>  // vertical
        combinationsIterator(OrdinalVector(3, 3, 3)).toList.map(combination => buildVerticalCurve(combination, precision))
      case 2 =>  // mixed
        combinationsIterator(OrdinalVector(3, 3, 3)).toList.map(combination => buildMixedCurve22(combination, precision))
      case -2 =>  // mixed
        combinationsIterator(OrdinalVector(3, 3)).toList.map(combination => buildMixedCurve31(combination, precision))
      case 1 =>  // horizontal
        combinationsIterator(OrdinalVector(3)).toList.map(combination => buildHorizontalCurve(combination, precision))
    }
  }
}

// three-dimensionals...

case class FactoryXYT(precision: Int, plys: Int) {
  def buildVerticalCurve(combination: OrdinalVector, precision: Int): ComposedCurve = {
    val (leftPrec, rightPrec) = decomposePrecision(combination, precision)

    val rightChild: Composable  = combination.size match {
      case 2 => DefaultDimensions.createNearDateTime(rightPrec)
      case 1 => DefaultDimensions.createLatitude(rightPrec)
      case _ => throw new Exception("Invalid right child specification")
    }
    val leftChild: Composable = combination.size match {
      case 2 => buildVerticalCurve(OrdinalVector(combination.toSeq.dropRight(1):_*), leftPrec)
      case 1 => DefaultDimensions.createLongitude(leftPrec)
      case _ => throw new Exception("Invalid left child specification")
    }

    val rawCurve = rawNWayCurve(combination, leftPrec, rightPrec)

    new ComposedCurve(rawCurve, Seq(leftChild, rightChild))
  }

  def buildHorizontalCurve(combination: OrdinalVector, precision: Int): ComposedCurve = {
    val tPrecision = precision / 3
    val yPrecision = (precision - tPrecision) >> 1
    val xPrecision = precision - tPrecision - yPrecision

    val rawCurve = rawNWayCurve(combination, xPrecision, yPrecision, tPrecision)

    new ComposedCurve(
      rawCurve,
      Seq(
        DefaultDimensions.createLongitude(xPrecision),
        DefaultDimensions.createLatitude(yPrecision),
        DefaultDimensions.createNearDateTime(tPrecision)
      )
    )
  }

  def getCurves: Seq[ComposedCurve] = {
    plys match {
      case 2 =>  // mixed
        combinationsIterator(OrdinalVector(3, 3)).toList.map(combination => buildVerticalCurve(combination, precision))
      case 1 =>  // horizontal
        combinationsIterator(OrdinalVector(3)).toList.map(combination => buildHorizontalCurve(combination, precision))
    }
  }
}

// two-dimensionals...

case class FactoryXY(precision: Int) {
  def buildCurve(combination: OrdinalVector, precision: Int): ComposedCurve = {
    val (leftPrec, rightPrec) = decomposePrecision(combination, precision)

    val rightChild: Composable = DefaultDimensions.createLatitude(rightPrec)
    val leftChild: Composable = DefaultDimensions.createLongitude(leftPrec)

    val rawCurve = rawNWayCurve(combination, leftPrec, rightPrec)

    new ComposedCurve(rawCurve, Seq(leftChild, rightChild))
  }

  def getCurves: Seq[ComposedCurve] =
    combinationsIterator(OrdinalVector(3)).toList.map(combination => buildCurve(combination, precision))
}