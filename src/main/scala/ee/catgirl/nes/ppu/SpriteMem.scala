package ee.catgirl.nes.ppu

import chisel3._
import chisel3.util._

class SpriteMem extends Module {
    val io = IO(new Bundle{
        val CE = Input(Bool())
        val lineStart = Input(Bool())
        val spriteEnable = Input(Bool())
        val exitingVblank = Input(Bool())
        val spriteSize = Input(Bool())
        val scanline = Input(UInt(9.W))
        val cycle = Input(UInt(9.W))
        val oamBus = Output(UInt(8.W))
        val loadOamPtr = Input(Bool())
        val loadOam = Input(Bool())
        val dataIn = Input(UInt(8.W))
        val spriteOverflow = Output(Bool())
        val sprite0Active = Output(Bool())
    })
    val spriteOverflow = Reg(Bool())
    io.spriteOverflow := spriteOverflow
    val sprite0Active = Reg(Bool())
    io.sprite0Active := sprite0Active

    val spriteTempMem = Mem(32,UInt(8.W))
    val oamMem = Mem(256,UInt(8.W))
    val oamPtr = Reg(UInt(8.W))
    val tempPtrHi = Reg(UInt(3.W))
    val state = Reg(UInt(2.W))
    val oamData = oamMem(oamPtr)
    val spriteTempMemPtr = Wire(UInt(5.W))
    val spriteYCoord = io.scanline - oamData
    val spriteHit = (!spriteYCoord(8,4).orR) & (io.spriteSize | !spriteYCoord(3))
    val nextOamPtr = Wire(UInt(8.W))
    val oamPtrIncrement = Wire(UInt(2.W))
    val sprite0Current = Reg(Bool())
    val oamWrapped = Reg(Bool())
    val spriteTempData = spriteTempMem(spriteTempMemPtr)

    when(!io.cycle(8)) {
        spriteTempMemPtr := Cat(tempPtrHi,oamPtr(1,0))
    }
    .elsewhen(!io.cycle(2)) {
        spriteTempMemPtr := Cat(io.cycle(5,3),io.cycle(1,0))
    }
    .otherwise {
        spriteTempMemPtr := Cat(io.cycle(5,3),3.U)
    }

    val oamBusSelector = Cat(io.spriteEnable, io.cycle(8), io.cycle(6), state, oamPtr(1,0))
    when(oamBusSelector === BitPat("b1_10_??_??")) {
        io.oamBus := spriteTempData
    }
    .elsewhen(oamBusSelector === BitPat("b1_??_00_??")) {
        io.oamBus := "b11111111".U
    }
    .elsewhen(oamBusSelector === BitPat("b1_??_01_00")) {
        io.oamBus := Cat(0.U(4.W), spriteYCoord(3,0))
    }
    .elsewhen(oamBusSelector === BitPat("b?_??_??_10")) {
        io.oamBus := Cat(oamData(7,5), 0.U(3.W), oamData(1,0))
    }
    .otherwise {
        io.oamBus := oamData
    }

    when(io.loadOam) {
        oamPtrIncrement := Cat(oamPtr(1,0).andR,1.B)
    }
    .elsewhen(state === 0.U) {
        oamPtrIncrement := 1.U
    }
    .elsewhen(state === 1.U) {
        when(oamPtr(1,0) === 0.U) {
            oamPtrIncrement := Cat(!spriteHit,spriteHit)
        }
        .otherwise {
            oamPtrIncrement := Cat(oamPtr(1,0) === 3.U, 1.B)
        }
    }
    .elsewhen(state === 3.U) {
        oamPtrIncrement := 3.U
    }
    .otherwise {//elsewhen(state === 2.U) {
        oamPtrIncrement := Cat(0.B, oamPtr(1,0) =/= 0.U)
    }
    val oamPtrHighTemp = Cat(0.B,oamPtr(7,2)) +& Cat(0.U(6.W),oamPtrIncrement(1))
    oamWrapped := oamPtrHighTemp(7)
    nextOamPtr := Cat(oamPtrHighTemp(6,0),oamPtr(1,0)+ oamPtrIncrement(0))

    when(io.CE) {
        when(io.loadOam) {
            oamMem(oamPtr) := Mux(oamPtr(1,0) === 2.U, io.dataIn & 0xE3.U, io.dataIn)
        }
        when((io.cycle(0) & io.spriteEnable) | io.loadOam | io.loadOamPtr) {
            oamPtr := Mux(io.loadOamPtr,io.dataIn,nextOamPtr)
        }
        when(io.spriteEnable & (state === 3.U) & spriteHit) {
            spriteOverflow := 1.B
        }
        sprite0Current := (state === 1.U) & !oamPtr(7,2).orR & spriteHit | sprite0Current

        when(!state(1)) {
            spriteTempMem(spriteTempMemPtr) := io.oamBus
        }
        
        when(io.cycle(0)) {
            when(!state(1) & (oamPtr(1,0).andR)) {
                tempPtrHi := tempPtrHi + 1.U
            }

            when(!state.orR) {
                state := ((tempPtrHi.andR) & (oamPtr(1,0).andR))
            }
            .elsewhen(state === 1.U) {
                when(oamWrapped) {
                    state := 2.U
                }
                .elsewhen((tempPtrHi.andR) & (oamPtr(1,0).andR)) {
                    state := 3.U
                }
                .otherwise {
                    state := 1.U
                }
            }
            .elsewhen(state === 3.U) {
                state := Mux(oamWrapped,2.U,1.U)
            }
            .elsewhen(state === 2.U) {
                state := 2.U
            }
        }

        when(io.lineStart) {
            state := 0.U
            tempPtrHi := 0.U
            oamPtr := 0.U
            sprite0Current := 0.B
            sprite0Active := sprite0Current
        }
        when(io.exitingVblank) {
            spriteOverflow := 0.B
        }
    }
}

