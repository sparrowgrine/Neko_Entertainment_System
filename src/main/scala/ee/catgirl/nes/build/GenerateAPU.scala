package ee.catgirl.nes.build

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import ee.catgirl.nes.apu._

object GenerateAPU extends App {
  (new ChiselStage).execute(Array("-X", "sverilog","--target-dir", "genrtl","--target:fpga"),Seq(ChiselGeneratorAnnotation(() => new PulseChannel(sq2 = false))))
  (new ChiselStage).execute(Array("-X", "sverilog","--target-dir", "genrtl","--target:fpga"),Seq(ChiselGeneratorAnnotation(() => new TriangleChannel())))
  (new ChiselStage).execute(Array("-X", "sverilog","--target-dir", "genrtl","--target:fpga"),Seq(ChiselGeneratorAnnotation(() => new NoiseChannel())))
  (new ChiselStage).execute(Array("-X", "sverilog","--target-dir", "genrtl","--target:fpga"),Seq(ChiselGeneratorAnnotation(() => new DMCChannel())))
  (new ChiselStage).execute(Array("-X", "sverilog","--target-dir", "genrtl","--target:fpga"),Seq(ChiselGeneratorAnnotation(() => new APU())))
}

