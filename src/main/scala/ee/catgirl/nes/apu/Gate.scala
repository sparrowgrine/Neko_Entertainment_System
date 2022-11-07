package ee.catgirl.nes.apu

import chisel3._

object Gate {
  def apply[T <: Data](in: T, gate : Bool) : T = {
    val mod = Module(new Gate(in))
    mod.io.in := in
    mod.io.ctrl := gate
    mod.io.out
  }
}

class Gate[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val in = Input(chiselTypeOf(gen))
    val ctrl = Input(Bool())
    val out = Output(chiselTypeOf(gen))
  })

  io.out := Mux(io.ctrl, io.in, 0.U.asTypeOf(gen))
}
