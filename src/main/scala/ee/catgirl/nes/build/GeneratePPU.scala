package ee.catgirl.nes.build

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import ee.catgirl.nes.PPU

object GeneratePPU extends App {
  (new ChiselStage).execute(Array("-X", "sverilog","--target-dir", "genrtl","--target:fpga"),Seq(ChiselGeneratorAnnotation(() => new PPU())))
}

