package ee.catgirl.nes.ppu

import chisel3._
import chisel3.util._

class Palette extends Module {
    val io = IO(new Bundle {
        val CE = Input(Bool())
        val addr = Input(UInt(5.W))
        val dataIn = Input(UInt(6.W))
        val dataOut = Output(UInt(6.W))
        val WE = Input(Bool())
    })

    val paletteMem = Mem(32,UInt(6.W))
    val paletteAddr = Mux(!io.addr(1,0).orR, 0.U,io.addr)
    io.dataOut := paletteMem(paletteAddr)

    when(io.CE & io.WE) {
        when(io.addr(3,2).orR & !io.addr(1,0).orR) {
            paletteMem(paletteAddr) := io.dataIn
        }
    }
}
