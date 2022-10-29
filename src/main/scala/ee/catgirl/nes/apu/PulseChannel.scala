package ee.catgirl.nes.apu

import chisel3._

class PulseChannel extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(2.W))
    val dataIn = Input(UInt(8.W))
    val en = Input(Bool())
    val out = Output(UInt(4.W))
  })

  class ControlRegDef extends Bundle {

  }
}
