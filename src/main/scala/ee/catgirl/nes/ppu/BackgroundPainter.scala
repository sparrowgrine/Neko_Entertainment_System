package ee.catgirl.nes.ppu
import chisel3._
import chisel3.util._

class BackgroundPainter extends Module {
  val io = IO(new Bundle {
    val CE = Input(Bool())
    val EN = Input(Bool())
    val cycle = Input(UInt(3.W))
    val fineXScroll = Input(UInt(3.W))
    val vReg = Input(UInt(15.W))
    val nameTable = Output(UInt(8.W))
    val vramDataIn = Input(UInt(8.W))
    val pixelOut = Output(UInt(4.W))
  })

  val nameTablePipe0 = RegInit(0.U(16.W))
  val nameTablePipe1 = RegInit(0.U(16.W))
  val attrTablePipe0 = RegInit(0.U(9.W))
  val attrTablePipe1 = RegInit(0.U(9.W))
  val nameTable = RegInit(0.U(8.W))
  io.nameTable := nameTable
  val attrTable = RegInit(0.U(2.W))
  val bgPixel0 = RegInit(0.U(8.W))
  val bgPixel1 = io.vramDataIn

  when(io.CE) {
    switch(io.cycle(2,0)) {
        is(1.U) {
            nameTable := io.vramDataIn
        }

        is(3.U) {
            when(!io.vReg(1) & !io.vReg(6)) {
                attrTable := io.vramDataIn(1,0)
            }
            .elsewhen(io.vReg(1) & !io.vReg(6)) {
                attrTable := io.vramDataIn(3,2)
            }
            .elsewhen(!io.vReg(1) & io.vReg(6)) {
                attrTable := io.vramDataIn(5,4)
            }
            .otherwise {
                attrTable := io.vramDataIn(7,6)
            }
        }
        is(5.U) {
            bgPixel0 := io.vramDataIn
        }
    }

    when(io.EN) {
        when(io.cycle(2,0).andR) {
            nameTablePipe0 := Cat(Reverse(bgPixel0),nameTablePipe0(8,1))
            nameTablePipe1 := Cat(Reverse(bgPixel1),nameTablePipe1(8,1))
            attrTablePipe0 := Cat(attrTable(0),attrTablePipe0(8,1))
            attrTablePipe1 := Cat(attrTable(1),attrTablePipe1(8,1))
        }
        .otherwise {
            nameTablePipe0 := Cat(nameTablePipe0(15),nameTablePipe0(15,1))
            nameTablePipe1 := Cat(nameTablePipe1(15),nameTablePipe1(15,1))
            attrTablePipe0 := Cat(attrTablePipe0(8),attrTablePipe0(8,1))
            attrTablePipe1 := Cat(attrTablePipe1(8),attrTablePipe1(8,1))
        }
    }
  }
  io.pixelOut := Cat(attrTablePipe1(io.fineXScroll),attrTablePipe0(io.fineXScroll),
                    nameTablePipe1(io.fineXScroll),nameTablePipe0(io.fineXScroll))
}
