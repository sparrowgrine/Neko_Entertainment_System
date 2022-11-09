package ee.catgirl.nes.ppu

import chisel3._
import chisel3.util._

class ScrollController extends Module {

    class VRamAddress extends Bundle {
        val fineYScroll = UInt(3.W)
        val nametableSelectHigh = Bool()
        val nametableSelectLow = Bool()
        val coarseYScroll = UInt(5.W)
        val coarseXScroll = UInt(5.W)
    }

    val io = IO(new Bundle {
        val CE = Input(Bool())
        val inRender = Input(Bool())
        val AB = Input(UInt(3.W))
        val dataIn = Input(UInt(8.W))
        val readStrobe = Input(Bool())
        val writeStrobe = Input(Bool())
        val inPreRender = Input(Bool())
        val cycle = Input(UInt(9.W))
        val scrollAddr = Output(UInt(15.W))
        val fineXScroll = Output(UInt(3.W))
    })
    val incrementSelector = RegInit(0.B)
    val vReg = RegInit(0.U.asTypeOf(new VRamAddress))
    val tReg = RegInit(0.U.asTypeOf(new VRamAddress))
    val fineXScroll = RegInit(0.U(3.W))
    io.fineXScroll := fineXScroll
    val addrLatch = RegInit(0.B)
    io.scrollAddr := vReg.asUInt

    when(io.CE) {
        when(io.inRender) {
            when(io.cycle(2,0) === 3.U & (io.cycle < 256.U || (io.cycle >= 320.U & io.cycle < 326.U))) {
                vReg.coarseXScroll := vReg.coarseXScroll + 1.U
                vReg.nametableSelectLow := vReg.nametableSelectLow ^ vReg.coarseXScroll.andR
            }

            when(io.cycle === 251.U) {
                vReg.fineYScroll := vReg.fineYScroll + 1.U
                when(vReg.fineYScroll.andR) {
                    when(vReg.coarseYScroll === 29.U) {
                        vReg.coarseYScroll := 0.U
                        vReg.nametableSelectHigh := !vReg.nametableSelectHigh
                    }
                    .otherwise {
                        vReg.fineYScroll := vReg.fineYScroll + 1.U
                    }
                }
            }

            when(io.cycle === 256.U) {
                vReg.nametableSelectLow := tReg.nametableSelectLow
                vReg.coarseXScroll := tReg.coarseXScroll   
            }

            when(io.cycle === 304.U & io.inPreRender) {
                vReg := tReg
            }
        }

        when(io.writeStrobe) {
            switch(io.AB) {
                is(0.U) {
                    incrementSelector := io.dataIn(2)
                    tReg.nametableSelectHigh := io.dataIn(1)
                    tReg.nametableSelectLow := io.dataIn(0)
                }
                is(5.U) {
                    when(!addrLatch) {
                        tReg.coarseXScroll := io.dataIn(7,3)
                        fineXScroll := io.dataIn(2,0)
                    }
                    .otherwise {
                        tReg.coarseYScroll := io.dataIn(7,3)
                        tReg.fineYScroll := io.dataIn(2,0)
                        
                    }
                    addrLatch := !addrLatch
                }
                is(6.U) {
                    when(!addrLatch) {
                        tReg := Cat(0.B,io.dataIn(5,0),tReg.asUInt(7,0)).asTypeOf(tReg)
                    }
                    .otherwise {
                        tReg := Cat(tReg.asUInt(14,8),io.dataIn).asTypeOf(tReg)
                        vReg := Cat(tReg.asUInt(14,8),io.dataIn).asTypeOf(vReg)
                    }
                    addrLatch := !addrLatch
                }
            }
        }
        when(io.readStrobe & io.AB === 2.U) {
            addrLatch := 0.B
        }

        when((io.readStrobe || io.writeStrobe) && io.AB === 7.U && !io.inRender) {
            vReg := (vReg.asUInt + Mux(incrementSelector,32.U,1.U)).asTypeOf(vReg)
        }
    }
}
