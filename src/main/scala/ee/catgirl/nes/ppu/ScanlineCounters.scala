package ee.catgirl.nes.ppu

import chisel3._
import chisel3.util._

class ScanlineCounters extends Module {
    val io = IO(new Bundle {
        val CE = Input(Bool())
        val inRender = Input(Bool())
        val scanline = Output(UInt(9.W))
        val cycle = Output(UInt(9.W))
        val inVblank = Output(Bool())
        val endOfLine = Output(Bool())
        val lastCycleGroup = Output(Bool())
        val enteringVblank = Output(Bool())
        val exitingVblank = Output(Bool())
        val inPreRender = Output(Bool())
    })

    val scanline = RegInit(0.U(9.W))
    io.scanline := scanline
    val cycle = RegInit(0.U(9.W))
    io.cycle := cycle
    val inVblank = RegInit(1.B)
    io.inVblank := inVblank
    val inPreRender = RegInit(0.B)
    io.inPreRender := inPreRender
    val secondFrame = RegInit(0.B)

    io.lastCycleGroup := (cycle(8,3) === 42.U)

    io.endOfLine := io.lastCycleGroup && (cycle(3,0) === Mux(inPreRender & secondFrame & io.inRender,3.U,4.U))

    io.enteringVblank := io.endOfLine & scanline === 240.U

    io.exitingVblank := io.endOfLine & scanline === 260.U

    when(io.CE) {
        cycle := Mux(io.endOfLine,0.U,cycle + 1.U)
        inVblank := Mux(io.enteringVblank,1.B,Mux(io.exitingVblank,0.B,inVblank))
        when(io.endOfLine) {
            scanline := Mux(io.exitingVblank, "b111111111".U(9.W), scanline + 1.U)
            inPreRender := io.exitingVblank
            when(io.exitingVblank) {
                secondFrame := !secondFrame
            }
        }
    }
}
