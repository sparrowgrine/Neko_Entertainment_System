package ee.catgirl.nes.ppu

import chisel3._
import chisel3.util._

class PPU extends Module {
  val io = IO(new Bundle{
    val CE = Input(Bool())
    val cpuAddr = Input(UInt(3.W))
    val cpuDataIn = Input(UInt(8.W))
    val cpuDataOut = Output(UInt(8.W))
    val cpuReadStrobe = Input(Bool())
    val cpuWriteStrobe = Input(Bool())
    val nmi = Output(Bool())
    val vramAddr = Output(UInt(14.W))
    val vramDataIn = Input(UInt(8.W))
    val vramDataOut = Output(UInt(8.W))
    val vramReadStrobe = Output(Bool())
    val vramWriteStrobe = Output(Bool())
    val scanline = Output(UInt(9.W))
    val cycle = Output(UInt(9.W))
    val pixelOut = Output(UInt(6.W))
  })

  val spritePattern = RegInit(0.B)
  val backgroundPattern = RegInit(0.B)
  val spriteSize = RegInit(0.B)
  val vblankNMIEnabled = RegInit(0.B)
  val colorDisable = RegInit(0.B)
  val backgroundClip = RegInit(0.B)
  val spriteClip = RegInit(0.B)
  val backgroundEnabled = RegInit(0.B)
  val spritesEnabled = RegInit(0.B)
  val colorEmphasis = RegInit(0.U(3.W))
  
  
  val nmiTriggered = Reg(Bool())
  val vramLatch = Reg(UInt(8.W))

  val scanlineCounter = Module(new ScanlineCounters)
  scanlineCounter.io.CE := io.CE
  io.scanline := scanlineCounter.io.scanline
  io.cycle := scanlineCounter.io.cycle

  val inRender = (backgroundEnabled | spritesEnabled) & !scanlineCounter.io.inVblank & (scanlineCounter.io.scanline =/= 240.U)
  scanlineCounter.io.inRender := inRender

  val scrollController = Module(new ScrollController)

  scrollController.io.CE := io.CE
  scrollController.io.inRender := inRender
  scrollController.io.cycle := scanlineCounter.io.cycle
  scrollController.io.AB := io.cpuAddr
  scrollController.io.dataIn := io.cpuDataIn
  scrollController.io.readStrobe := io.cpuReadStrobe
  scrollController.io.writeStrobe := io.cpuWriteStrobe
  scrollController.io.inPreRender := scanlineCounter.io.inPreRender
  val scrollAddr = scrollController.io.scrollAddr

  val backgroundPainter = Module(new BackgroundPainter) 
  backgroundPainter.io.CE := io.CE
  backgroundPainter.io.EN := !scanlineCounter.io.lastCycleGroup
  backgroundPainter.io.cycle := scanlineCounter.io.cycle(2,0)
  backgroundPainter.io.fineXScroll := scrollController.io.fineXScroll
  backgroundPainter.io.vReg := scrollController.io.scrollAddr
  backgroundPainter.io.vramDataIn := io.vramDataIn

  val showBackground = (backgroundClip | io.cycle(7,3).orR) & backgroundEnabled
  val backgroundPixel = Cat(backgroundPainter.io.pixelOut(3,2), Mux(showBackground,backgroundPainter.io.pixelOut(1,0),0.U))


  val spriteMem = Module(new SpriteMem)
  spriteMem.io.CE := io.CE
  spriteMem.io.lineStart := (backgroundEnabled | spritesEnabled) & (scanlineCounter.io.exitingVblank || scanlineCounter.io.endOfLine && !scanlineCounter.io.inVblank)
  spriteMem.io.spriteEnable := scanlineCounter.io.inRender
  spriteMem.io.exitingVblank := scanlineCounter.io.exitingVblank
  spriteMem.io.spriteSize := spriteSize
  spriteMem.io.cycle := scanlineCounter.io.cycle
  spriteMem.io.scanline := scanlineCounter.io.scanline
  spriteMem.io.loadOamPtr := io.cpuWriteStrobe & (io.cpuAddr === 3.U)
  spriteMem.io.loadOam := io.cpuWriteStrobe & (io.cpuAddr === 4.U)
  spriteMem.io.dataIn := io.cpuDataIn

  val spriteAddressGenerator = Module(new SpriteAddressGenerator)
  spriteAddressGenerator.io.CE := io.CE
  spriteAddressGenerator.io.EN := scanlineCounter.io.cycle(8) & ! scanlineCounter.io.cycle(6)
  spriteAddressGenerator.io.cycle := scanlineCounter.io.cycle(2,0)
  spriteAddressGenerator.io.spriteSize := spriteSize
  spriteAddressGenerator.io.spritePattern := spritePattern
  spriteAddressGenerator.io.tempData := spriteMem.io.oamBus
  spriteAddressGenerator.io.vramData := io.vramDataIn

  val spriteController = Module(new SpriteController) 
  spriteController.io.CE := io.CE
  spriteController.io.EN := !scanlineCounter.io.cycle(8)
  spriteController.io.load := spriteAddressGenerator.io.load
  spriteController.io.dataIn := spriteAddressGenerator.io.spriteDataOut

  val showSprite = (spriteClip || io.cycle(7,3).orR) & spritesEnabled
  val spritePixel = Cat(spriteController.io.spriteOut(4,2),Mux(showSprite,spriteController.io.spriteOut(1,0),0.U))

  val spriteZeroBackgroundHit = Reg(Bool())
  when(io.CE) {
    when(scanlineCounter.io.exitingVblank) {
      spriteZeroBackgroundHit := 0.B
    }
    .elsewhen(scanlineCounter.io.inRender &&
              !scanlineCounter.io.cycle(8) &&
              !scanlineCounter.io.cycle(7,0).andR &&
              !scanlineCounter.io.inPreRender &&
              spriteMem.io.sprite0Active &&
              spriteController.io.isFirstSprite &&
              showSprite &&
              backgroundPixel(1,0).orR
              ) {
      spriteZeroBackgroundHit := 1.B
    }
  }

  val isSpritePixel = !(spritePixel(4) && backgroundPixel(1,0).orR) && spritePixel(1,0).orR
  val outPixel = Mux(isSpritePixel,spritePixel,backgroundPixel)

  when(!scanlineCounter.io.inRender) {
    io.vramAddr := scrollAddr(13,0)
  }
  .elsewhen(io.cycle(2,1) === 0.U) {
    io.vramAddr := Cat(1.B,0.B,scrollAddr(11,0))
  }
  .elsewhen(io.cycle(2,1) === 1.U) {
    io.vramAddr := Cat(1.B,0.B,scrollAddr(11,10),1.B,1.B,1.B,1.B,scrollAddr(9,7),scrollAddr(4,2))
  }
  .elsewhen(io.cycle(8) & !io.cycle(6)) {
    io.vramAddr := Cat(0.B,spriteAddressGenerator.io.vramAddr)
  }
  .otherwise {
    io.vramAddr := Cat(0.B,backgroundPattern,backgroundPainter.io.nameTable,io.cycle(1),scrollAddr(14,12))
  }

  val isPaletteAddr = scrollAddr(13,8).andR

  io.vramReadStrobe := (io.cpuReadStrobe & (io.cpuAddr === 7.U)) | scanlineCounter.io.inRender & !io.cycle(0) & scanlineCounter.io.endOfLine

  io.vramWriteStrobe := (io.cpuWriteStrobe & (io.cpuAddr === 7.U)) & !scanlineCounter.io.inRender & !isPaletteAddr

  val palette = Module(new Palette)
  palette.io.CE := io.CE
  palette.io.addr := Mux(scanlineCounter.io.inRender, Cat(isSpritePixel,spritePixel(3,0)),Mux(isPaletteAddr,scrollAddr(4,0),0.U))
  palette.io.dataIn := io.cpuDataIn(5,0)
  palette.io.WE := io.cpuWriteStrobe & (io.cpuAddr === 7.U) & isPaletteAddr

  io.pixelOut := Mux(colorDisable,Cat(palette.io.dataOut(5,4),0.U(4.W)),palette.io.dataOut)

  when(io.CE) {
    when(io.cpuWriteStrobe) {
      switch(io.cpuAddr) {
        is(0.U) {
          vblankNMIEnabled := io.cpuDataIn(7)
          spriteSize := io.cpuDataIn(5)
          backgroundPattern := io.cpuDataIn(4)
          spritePattern := io.cpuDataIn(3)
        }
        is(1.U) {
          colorEmphasis := io.cpuDataIn(7,5)
          spritesEnabled := io.cpuDataIn(4)
          backgroundEnabled := io.cpuDataIn(3)
          spriteClip := io.cpuDataIn(2)
          backgroundClip := io.cpuDataIn(1)
          colorDisable := io.cpuDataIn(0)
        }
      }
    }

    when(scanlineCounter.io.exitingVblank) {
      nmiTriggered := 0.B
    }
    when(scanlineCounter.io.enteringVblank) {
      nmiTriggered := 1.B
    }
    when(io.cpuReadStrobe & (io.cpuAddr === 2.U)) {
      nmiTriggered := 0.B
    }
  }

  io.nmi := nmiTriggered & vblankNMIEnabled


  val vramStrobePrev = RegInit(0.B)
  when(io.CE) {
    vramStrobePrev := io.vramReadStrobe
    when(vramStrobePrev) {
      vramLatch := io.vramDataIn
    }
  }

  io.vramDataOut := io.cpuDataIn

  val cpuDataOut = Reg(UInt(8.W))

  when(io.cpuAddr === 2.U) {
    cpuDataOut := Cat(nmiTriggered,spriteZeroBackgroundHit,spriteMem.io.spriteOverflow,0.U(5.W))
  }
  when(io.cpuAddr === 4.U) {
    cpuDataOut := spriteMem.io.oamBus
  }

  io.cpuDataOut := cpuDataOut

}
