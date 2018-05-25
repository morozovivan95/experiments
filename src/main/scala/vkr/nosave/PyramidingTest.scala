package vkr.nosave

import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.pyramid.Pyramid
import geotrellis.spark.tiling._
import geotrellis.spark.{TileLayerRDD, _}
import geotrellis.vector._
import org.apache.spark._
import org.apache.spark.rdd._

object PyramidingTest {

  val inputPath = "wasb:///etl-experiments/mosaic"
  val layerPath = "wasb:///vkr/pyramiding/layer"

  def main(args: Array[String]): Unit = {
    val conf =
      new SparkConf()
        .setMaster("yarn")
        .setAppName("PyramidingTest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")

    val sc = new SparkContext(conf)
    try {
      run(sc)
    } finally {
      sc.stop()
    }
  }

  def run(implicit sc: SparkContext): Unit = {

    val inputRdd: RDD[(ProjectedExtent, Tile)] =
      sc.hadoopGeoTiffRDD(inputPath)

    val layoutScheme = FloatingLayoutScheme(256)

    val (_, rasterMetaData) =
      TileLayerMetadata.fromRdd(inputRdd, layoutScheme)

    val tiled: RDD[(SpatialKey, Tile)] =
      inputRdd
        .tileToLayout(rasterMetaData.cellType, rasterMetaData.layout, Bilinear)

    val rdd: RDD[(SpatialKey, Tile)] with Metadata[TileLayerMetadata[SpatialKey]] =
      TileLayerRDD(tiled, rasterMetaData)

    Pyramid.upLevels(rdd, layoutScheme, 0, 8, Bilinear) { (rdd, _) =>
      rdd.count()
    }
  }
}
