package ee.catgirl.nes.ppu

import chisel3._
import chisel3.util._
import com.google.protobuf.ByteOutput


class SpriteController extends Module {
    val io = IO(new Bundle {
        val CE = Input(Bool())
        val EN = Input(Bool())
        val load = Input(UInt(4.W))
        val dataIn = Input(UInt(27.W))
        val spriteOut = Output(UInt(5.W))
        val isFirstSprite = Output(Bool())
    })

    val sprites = for(i <- 0 to 7) yield {
        val sprite = Module(new Sprite)
        sprite.io.CE := io.CE
        sprite.io.EN := io.EN
        sprite.io.load := io.load
        sprite
    }
    sprites(7).io.dataIn := io.dataIn
    for(i <- (0 to 6)) {
        sprites(i).io.dataIn := sprites(i+1).io.dataOut
    }

    io.spriteOut := PriorityMux(
        for(i <- 0 to 7) yield {
            sprites(i).io.spriteOut(1,0).orR -> sprites(i).io.spriteOut
        }
    )
    io.isFirstSprite := sprites(0).io.spriteOut(1,0).orR
}


class Sprite extends Module {
    val io = IO(new Bundle {
        val CE = Input(Bool())
        val EN = Input(Bool())
        val load = Input(UInt(4.W))
        val dataIn = Input(UInt(27.W))
        val dataOut = Output(UInt(27.W))
        val spriteOut = Output(UInt(5.W))
    })
  
    val colorHi = Reg(UInt(2.W))
    val xCoord = Reg(UInt(8.W))
    val pixel1 = Reg(UInt(8.W))
    val pixel2 = Reg(UInt(8.W))
    val priority = Reg(Bool())
    val active = WireDefault(xCoord === 0.U)

    when(io.CE) {
        when(io.EN) {
            when(!active) {
                xCoord := xCoord - 1.U
            }
            .otherwise {
                pixel1 := pixel1 >> 1
                pixel2 := pixel2 >> 1
            }
        }

        when(io.load(0)) {
            pixel1 := io.dataIn(26,19)
        }
        when(io.load(1)) {
            pixel2 := io.dataIn(18,11)
        }
        when(io.load(1)) {
            xCoord := io.dataIn(10,3)
        }
        when(io.load(1)) {
            colorHi := io.dataIn(2,1)
            priority := io.dataIn(0)
        }
    }
    io.spriteOut := Cat(priority,colorHi,active & pixel2(0),active & pixel1(0))
    io.dataOut := Cat(pixel1,pixel2,xCoord,colorHi,priority)
}
