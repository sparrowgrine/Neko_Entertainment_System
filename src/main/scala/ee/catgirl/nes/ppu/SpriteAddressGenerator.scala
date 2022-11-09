package ee.catgirl.nes.ppu

import chisel3._
import chisel3.util._

class SpriteAddressGenerator extends Module {
    val io = IO(new Bundle {
        val CE = Input(Bool())
        val EN = Input(Bool())
        val spriteSize = Input(Bool())
        val spritePattern = Input(Bool())
        val cycle = Input(UInt(2.W))
        val tempData = Input(UInt(8.W))
        val vramAddr = Output(UInt(13.W))
        val vramData = Input(UInt(8.W))
        val load = Output(UInt(4.W))
        val spriteDataOut = Output(UInt(27.W))
    })

    val tile = Reg(UInt(8.W))
    val yCoord = Reg(UInt(4.W))
    val xFlip = Reg(Bool())
    val yFlip = Reg(Bool())
    val loadY = io.cycle === 0.U
    val loadTile = io.cycle === 1.U
    val loadAttribute = (io.cycle === 2.U) & io.EN
    val loadX = (io.cycle === 3.U) & io.EN
    val loadPixel1 = (io.cycle === 5.U) & io.EN
    val loadPixel2 = (io.cycle === 7.U) & io.EN

    val invalidSprite = Reg(Bool())

    val vramData = Mux(invalidSprite,0.U, Mux(xFlip,io.vramData,Reverse(io.vramData)))
    val flippedYCoord = yCoord ^ Fill(4,yFlip)
    io.load := Cat(loadPixel1,loadPixel2,loadX,loadAttribute)
    io.spriteDataOut := Cat(vramData,vramData,io.tempData,io.tempData(1,0),io.tempData(5))
    io.vramAddr := Cat(Mux(io.spriteSize,tile(0),io.spritePattern),tile(7,1),Mux(io.spriteSize,flippedYCoord(3),tile(0)),io.cycle(1),flippedYCoord(2,0))

    when(io.CE) {
        when(loadY) {
            yCoord := io.tempData(3,0)
        }
        when(loadTile) {
            tile := io.tempData
        }
        when(loadAttribute) {
            yFlip := io.tempData(7)
            xFlip := io.tempData(6)
            invalidSprite := io.tempData(4)
        }
    }
}
