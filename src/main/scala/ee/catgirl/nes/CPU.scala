package ee.catgirl.nes

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

import scala.language.implicitConversions

class ALU extends Module {
  val io = IO(new Bundle {
    val right = Input(Bool())
    val op = Input(UInt(4.W))
    val AI = Input(UInt(8.W))
    val BI = Input(UInt(8.W))
    val CI = Input(Bool())
    val BCD = Input(Bool())
    val OUT = Output(UInt(8.W))
    val CO = Output(Bool())
    val V = Output(Bool())
    val Z = Output(Bool())
    val N = Output(Bool())
    val HC = Output(Bool())
    val RDY = Input(Bool())
  })

  val OUT = Reg(UInt(8.W))
  io.OUT := OUT
  val CO = Reg(Bool())
  io.CO := CO
  val N = Reg(Bool())
  io.N := N
  val HC = Reg(Bool())
  io.HC := HC

  val AI7 = Reg(Bool())
  val BI7 = Reg(Bool())
  val temp_logic = Wire(UInt(9.W))
  val temp_BI = Wire(UInt(8.W))
  val temp_l = Wire(UInt(5.W))
  val temp_h = Wire(UInt(5.W))
  val temp = WireDefault(Cat(temp_h(4,0), temp_l(3, 0)))
  val adder_ci = WireDefault(Mux(io.right | (io.op(3, 2) === 3.U), 0.B, io.CI))

  when(io.right) {
    temp_logic := Cat(io.AI(0), io.CI, io.AI(7, 1))
  }
  .otherwise {
    temp_logic := VecInit(io.AI | io.BI,io.AI & io.BI,io.AI ^ io.BI,io.AI)(io.op(1,0))
  }

  temp_BI := VecInit(io.BI,~io.BI,temp_logic,0.U)(io.op(3,2))

  val HC9 = WireDefault(io.BCD & (temp_l(3,1) >= 5.U))
  val CO9 = WireDefault(io.BCD & (temp_h(3,1) >= 5.U))
  val temp_HC = WireDefault(temp_l(4) | HC9)
  temp_l := temp_logic(3,0) +& temp_BI(3,0) + adder_ci
  temp_h := temp_logic(8,4) +& temp_BI(7,4) + temp_HC

  when(io.RDY) {
    AI7 := io.AI(7)
    BI7 := temp_BI(7)
    OUT := temp(7,0)
    CO := temp(8) | CO9
    N := temp(7)
    HC := temp_HC
  }

  io.V := AI7 ^ BI7 ^ CO ^ N
  io.Z := ~OUT.orR
}

class CPU extends Module {

  trait Innable[A] {
    def ===(that: UInt): Bool
  }

  implicit class BitPatExt(val b: BitPat) extends Innable[BitPat]  {
    def ===(that: UInt): Bool = {
      b === that
    }
  }
  implicit class UIntExt(val u: UInt) extends Innable[UInt]  {
    def ===(that: UInt): Bool = {
      u === that
    }
    def in[T](args: Innable[T]*): Bool = {
      var result = 0.B
      for (arg <- args) {
        result = result || (arg === u)
      }
      result
    }
  }


  val io = IO(new Bundle {
    val AB = Output(UInt(16.W))
    val DI = Input(UInt(8.W))
    val DO = Output(UInt(8.W))
    val WE = Output(Bool())
    val IRQ = Input(Bool())
    val NMI = Input(Bool())
    val RDY = Input(Bool())
    val CE = Input(Bool())
  })

  val PC = Reg(UInt(16.W))
  val ABL = Reg(UInt(8.W))
  val ABH = Reg(UInt(8.W))
  val ADD = Wire(UInt(8.W))

  val AIHOLD = Reg(UInt(8.W))
  val BIHOLD = Reg(UInt(8.W))
  val AIMUX = Wire(UInt(8.W))
  val BIMUX = Wire(UInt(8.W))

  val DIHOLD = Reg(UInt(8.W))
  val DIMUX = Wire(UInt(8.W))

  val IRHOLD = Reg(UInt(8.W))
  val IRHOLD_valid = RegInit(0.B)

  val AXYS = Mem(4,UInt(8.W))

  val C = Reg(Bool())
  val Z = Reg(Bool())
  val I = Reg(Bool())
  val D = Reg(Bool())
  val V = Reg(Bool())
  val N = Reg(Bool())

  val AZ = Wire(Bool())
  val AV = Wire(Bool())
  val AN = Wire(Bool())
  val HC = Wire(Bool())

  val AI = Wire(UInt(8.W))
  val BI = Wire(UInt(8.W))
  val IR = Wire(UInt(8.W))
  val CI = Wire(Bool())
  val CO = Wire(Bool())
  val PCH = PC(15,8)
  val PCL = PC(7,0)

  val NMI_edge = RegInit(0.B)

  val regsel = Wire(UInt(2.W))
  val regfile = AXYS(regsel)

  object RegSel extends ChiselEnum {
    val SEL_A = 0.U
    val SEL_S = 1.U
    val SEL_X = 2.U
    val SEL_Y = 3.U
  }

  import RegSel._


  val P = WireDefault(Cat(N,V,3.U(2.W),D,I,Z,C))

  val PC_inc = Wire(Bool())
  val PC_temp = Wire(UInt(16.W))
  val src_reg = Reg(UInt(2.W))
  val dst_reg = Reg(UInt(2.W))

  val index_y = Reg(Bool())
  val load_reg = Reg(Bool())
  val inc = Reg(Bool())
  val write_back = Reg(Bool())
  val load_only = Reg(Bool())
  val store = Reg(Bool())
  val adc_sbc = Reg(Bool())
  val compare = Reg(Bool())
  val shift = Reg(Bool())
  val rotate = Reg(Bool())
  val backwards = Reg(Bool())
  val cond_true = Wire(Bool())
  val cond_code = Reg(UInt(3.W))
  val shift_right = Reg(Bool())
  val alu_shift_right = Wire(Bool())
  val op = Reg(UInt(4.W))
  val alu_op = Wire(UInt(4.W))

  val adc_bcd = Reg(Bool())
  val adj_bcd = Reg(Bool())

  val bit_ins = Reg(Bool())
  val plp = Reg(Bool())
  val php = Reg(Bool())
  val clc = Reg(Bool())
  val sec = Reg(Bool())
  val cld = Reg(Bool())
  val sed = Reg(Bool())
  val cli = Reg(Bool())
  val sei = Reg(Bool())
  val clv = Reg(Bool())
  val brk = Reg(Bool())
  val res = RegInit(1.B)

  object AluOps extends ChiselEnum {
    val OP_OR = "b1100".U(4.W)
    val OP_AND = "b1101".U(4.W)
    val OP_EOR = "b1110".U(4.W)
    val OP_ADD = "b0011".U(4.W)
    val OP_SUB = "b0111".U(4.W)
    val OP_ROL = "b1011".U(4.W)
    val OP_A = "b1111".U(4.W)
  }

  import AluOps._


object CPUState extends ChiselEnum {
  val ABS0 = 0.U(6.W) // ABS     - fetch LSB
  val ABS1 = 1.U(6.W) // ABS     - fetch MSB
  val ABSX0 = 2.U(6.W) // ABS, X  - fetch LSB and send to ALU (+X)
  val ABSX1 = 3.U(6.W) // ABS, X  - fetch MSB and send to ALU (+Carry)
  val ABSX2 = 4.U(6.W) // ABS, X  - Wait for ALU (only if needed)
  val BRA0 = 5.U(6.W) // Branch  - fetch offset and send to ALU (+PC[7:0])
  val BRA1 = 6.U(6.W) // Branch  - fetch opcode, and send PC[15:8] to ALU
  val BRA2 = 7.U(6.W) // Branch  - fetch opcode (if page boundary crossed)
  val BRK0 = 8.U(6.W) // BRK/IRQ - push PCH, send S to ALU (-1)
  val BRK1 = 9.U(6.W) // BRK/IRQ - push PCL, send S to ALU (-1)
  val BRK2 = 10.U(6.W) // BRK/IRQ - push P, send S to ALU (-1)
  val BRK3 = 11.U(6.W) // BRK/IRQ - write S, and fetch @ fffe
  val DECODE = 12.U(6.W) // IR is valid, decode instruction, and write prev reg
  val FETCH = 13.U(6.W) // fetch next opcode, and perform prev ALU op
  val INDX0 = 14.U(6.W) // (ZP,X)  - fetch ZP address, and send to ALU (+X)
  val INDX1 = 15.U(6.W) // (ZP,X)  - fetch LSB at ZP+X, calculate ZP+X+1
  val INDX2 = 16.U(6.W) // (ZP,X)  - fetch MSB at ZP+X+1
  val INDX3 = 17.U(6.W) // (ZP,X)  - fetch data
  val INDY0 = 18.U(6.W) // (ZP),Y  - fetch ZP address, and send ZP to ALU (+1)
  val INDY1 = 19.U(6.W) // (ZP),Y  - fetch at ZP+1, and send LSB to ALU (+Y)
  val INDY2 = 20.U(6.W) // (ZP),Y  - fetch data, and send MSB to ALU (+Carry)
  val INDY3 = 21.U(6.W) // (ZP),Y) - fetch data (if page boundary crossed)
  val JMP0 = 22.U(6.W) // JMP     - fetch PCL and hold
  val JMP1 = 23.U(6.W) // JMP     - fetch PCH
  val JMPI0 = 24.U(6.W) // JMP IND - fetch LSB and send to ALU for delay (+0)
  val JMPI1 = 25.U(6.W) // JMP IND - fetch MSB, proceed with JMP0 state
  val JSR0 = 26.U(6.W) // JSR     - push PCH, save LSB, send S to ALU (-1)
  val JSR1 = 27.U(6.W) // JSR     - push PCL, send S to ALU (-1)
  val JSR2 = 28.U(6.W) // JSR     - write S
  val JSR3 = 29.U(6.W) // JSR     - fetch MSB
  val PULL0 = 30.U(6.W) // PLP/PLA - save next op in IRHOLD, send S to ALU (+1)
  val PULL1 = 31.U(6.W) // PLP/PLA - fetch data from stack, write S
  val PULL2 = 32.U(6.W) // PLP/PLA - prefetch op, but don't increment PC
  val PUSH0 = 33.U(6.W) // PHP/PHA - send A to ALU (+0)
  val PUSH1 = 34.U(6.W) // PHP/PHA - write A/P, send S to ALU (-1)
  val READ = 35.U(6.W) // Read memory for read/modify/write (INC, DEC, shift)
  val REG = 36.U(6.W) // Read register for reg-reg transfers
  val RTI0 = 37.U(6.W) // RTI     - send S to ALU (+1)
  val RTI1 = 38.U(6.W) // RTI     - read P from stack
  val RTI2 = 39.U(6.W) // RTI     - read PCL from stack
  val RTI3 = 40.U(6.W) // RTI     - read PCH from stack
  val RTI4 = 41.U(6.W) // RTI     - read PCH from stack
  val RTS0 = 42.U(6.W) // RTS     - send S to ALU (+1)
  val RTS1 = 43.U(6.W) // RTS     - read PCL from stack
  val RTS2 = 44.U(6.W) // RTS     - write PCL to ALU, read PCH
  val RTS3 = 45.U(6.W) // RTS     - load PC and increment
  val WRITE = 46.U(6.W) // Write memory for read/modify/write
  val ZP0 = 47.U(6.W) // Z-page  - fetch ZP address
  val ZPX0 = 48.U(6.W) // ZP, X   - fetch ZP, and send to ALU (+X)
  val ZPX1 = 49.U(6.W) // ZP, X   - load from memory
}

  import CPUState._

  val state = RegInit(BRK0)

  val lastState = RegInit(ZPX1)

  when(io.CE) {
    lastState := state
  }

//  assert(state =/= lastState)

  when(state in DECODE) {
      when((io.IRQ & ~I) | NMI_edge) {
        PC_temp := Cat(ABH, ABL)
      }.otherwise {
        PC_temp := PC
      }
    }
      .elsewhen(state in(JMP1, JMPI1, JSR3, RTS3, RTI4)) {
        PC_temp := Cat(DIMUX, ADD)
      }
      .elsewhen(state in BRA1) {
        PC_temp := Cat(ABH, ADD)
      }
      .elsewhen(state in BRA2) {
        PC_temp := Cat(ADD, PCL)
      }
      .elsewhen(state in BRK2) {
        PC_temp := Mux(res, 0xfffc.U(16.W), Mux(NMI_edge, 0xfffa.U(16.W), 0xfffe.U(16.W)))
      }
      .otherwise {
        PC_temp := PC
      }

    when(state in DECODE) {
      when((~I & io.IRQ) | NMI_edge) {
        PC_inc := 0.B
      }
        .otherwise {
          PC_inc := 1.B
        }
    }
      .elsewhen(state in(ABS0, ABSX0, FETCH, BRA0, BRA2, BRK3, JMPI1, JMP1, RTI4, RTS3)) {
        PC_inc := 1.B
      }
      .elsewhen(state in BRA1) {
        PC_inc := ~(CO ^ backwards)
      }
      .otherwise {
        PC_inc := 0.B
      }

    when(io.RDY & io.CE) {
      //    when((PC =/= pc_temp + pc_inc) && (state in DECODE)) {
      //      printf("PC: %x\n",pc_temp)
      //    }
      PC := Mux(state in JMPI1, Cat(PC_temp(15, 8), PC_temp(7, 0) + PC_inc), PC_temp +& PC_inc)
    }

    val ZEROPAGE = 0.U(8.W)
    val STACKPAGE = 1.U(8.W)

    when(state in(ABSX1, INDX3, INDY2, JMP1, JMPI1, RTI4, ABS1)) {
      io.AB := Cat(DIMUX, ADD)
    }
      .elsewhen(state in(BRA2, INDY3, ABSX2)) {
        io.AB := Cat(ADD, ABL)
      }
      .elsewhen(state in BRA1) {
        io.AB := Cat(ABH, ADD)
      }
      .elsewhen(state in(JSR0, PUSH1, RTS0, RTI0, BRK0)) {
        io.AB := Cat(STACKPAGE, regfile)
      }
      .elsewhen(state in(BRK1, JSR1, PULL1, RTS1, RTS2, RTI1, RTI2, RTI3, BRK2)) {
        io.AB := Cat(STACKPAGE, ADD)
      }
      .elsewhen(state in(INDY1, INDX1, ZPX1, INDX2)) {
        io.AB := Cat(ZEROPAGE, ADD)
      }
      .elsewhen(state in(ZP0, INDY0)) {
        io.AB := Cat(ZEROPAGE, DIMUX)
      }
      .elsewhen(state in(REG, READ, WRITE)) {
        io.AB := Cat(ABH, ABL)
      }
      .otherwise {
        io.AB := PC
      }

    when(io.RDY & io.CE && state =/= PUSH0 && state =/= PUSH1 && state =/= PULL0 && state =/= PULL1 && state =/= PULL2) {
      ABL := io.AB(7, 0)
      ABH := io.AB(15, 8)
    }

    when(state in WRITE) {
      io.DO := ADD
    }
    .elsewhen(state in(JSR0, BRK0)) {
      io.DO := PCH
    }
    .elsewhen(state in(JSR1, BRK1)) {
      io.DO := PCL
    }
    .elsewhen(state in PUSH1) {
      io.DO := Mux(php, P, ADD)
    }
    .elsewhen(state in BRK2) {
      io.DO := Mux((io.IRQ | NMI_edge), P & "b11101111".U(8.W), P)
    }
    .otherwise {
      io.DO := regfile
    }

    when(state in(BRK0, BRK1, BRK2, JSR0, JSR1, PUSH1, WRITE)) {
      io.WE := 1.B
    }
      .elsewhen(state in(INDX3, INDY3, ABSX2, ABS1, ZPX1, ZP0)) {
        io.WE := store
      }
      .otherwise {
        io.WE := 0.B
      }

    val write_register = Wire(Bool())

    when(state in DECODE) {
      write_register := load_reg & ~plp
    }
      .elsewhen(state in(PULL1, RTS2, RTI3, BRK3, JSR0, JSR2)) {
        write_register := 1.B
      }
      .otherwise {
        write_register := 0.B
      }

    adj_bcd := 0.B

    val adjl = Wire(UInt(4.W))
    val adjh = Wire(UInt(4.W))

    adjl := Lookup(Cat(adj_bcd, adc_bcd, HC), 0.U, IndexedSeq(
      BitPat("b100") -> 10.U,
      BitPat("b111") -> 6.U
    ))

    adjh := Lookup(Cat(adj_bcd, adc_bcd, CO), 0.U, IndexedSeq(
      BitPat("b100") -> 10.U,
      BitPat("b111") -> 6.U
    ))

    when(write_register & io.RDY) {
      AXYS(regsel) := Mux(state in JSR0, DIMUX, Cat(ADD(7, 4) + adjh, ADD(3, 0) + adjl))
    }

    when(state in(INDY1, INDX0, ZPX0, ABSX0)) {
      regsel := Mux(index_y, SEL_Y, SEL_X)
    }
      .elsewhen(state in DECODE) {
        regsel := dst_reg
      }
      .elsewhen(state in(BRK0, BRK3, JSR0, JSR2, PULL0, PULL1, PUSH1, RTI0, RTI3, RTS0, RTS2)) {
        regsel := SEL_S
      }
      .otherwise {
        regsel := src_reg
      }

    val alu: ALU = Module(new ALU())
    alu.io.op := alu_op
    alu.io.right := alu_shift_right
    alu.io.AI := AIMUX
    alu.io.BI := BIMUX
    alu.io.CI := CI
    alu.io.BCD := /*adc_bcd & (state in FETCH)*/ false.B // BCD Disabled in NES CPU.
    CO := alu.io.CO
  val outHold = RegInit(0.U(8.W))
  when(RegNext(io.CE)) {
    outHold := alu.io.OUT
  }

  ADD := alu.io.OUT
    AV := alu.io.V
    AZ := alu.io.Z
    AN := alu.io.N
    HC := alu.io.HC
    alu.io.RDY := io.RDY

    when(state in(FETCH, REG, READ)) {
      alu_op := op
    }
      .elsewhen(state in BRA1) {
        alu_op := Mux(backwards, OP_SUB, OP_ADD)
      }
      .elsewhen(state in(PUSH1, BRK0, BRK1, BRK2, JSR0, JSR1)) {
        alu_op := OP_SUB
      }
      .otherwise {
        alu_op := OP_ADD
      }

    when(state in(FETCH, REG, READ)) {
      alu_shift_right := shift_right
    }
      .otherwise {
        alu_shift_right := 0.U
      }

    when(io.RDY & io.CE) {
      backwards := DIMUX(7)
    }

    when(state in(JSR1, RTS1, RTI1, RTI2, BRK1, BRK2, INDX1)) {
      AI := ADD
    }
    .elsewhen(state in(REG, ZPX0, INDX0, ABSX0, RTI0, RTS0, JSR0, JSR2, BRK0, PULL0, INDY1, PUSH0, PUSH1)) {
      AI := regfile
    }
    .elsewhen(state in(BRA0, READ)) {
      AI := DIMUX
    }
    .elsewhen(state in BRA1) {
      AI := ABH
    }
    .elsewhen(state in FETCH) {
      AI := Mux(load_only, 0.U, regfile)
    }
    .otherwise {
      AI := 0.U
    }

    when(RegNext(io.CE)) {
      AIHOLD := AI
      BIHOLD := BI
    }

  AIMUX := Mux(io.CE ,AIHOLD,AI)
  BIMUX := Mux(io.CE ,BIHOLD,BI)

    when(state in(BRA1, RTS1, RTI0, RTI1, RTI2, INDX1, READ, REG, JSR0, JSR1, JSR2, BRK0, BRK1, BRK2, PUSH0, PUSH1, PULL0, RTS0)) {
      BI := 0.U
    }
    .elsewhen(state in BRA0) {
      BI := PCL
    }
    .otherwise {
      BI := DIMUX
    }

    when(state in(INDY2, BRA1, ABSX1)) {
      CI := CO
    }
    .elsewhen(state in(READ, REG)) {
      CI := Mux(rotate, C, Mux(shift, 0.U, inc))
    }
    .elsewhen(state in FETCH) {
      CI := Mux(rotate, C, Mux(compare, 1.U, Mux(shift | load_only, 0.U, C)))
    }
    .elsewhen(state in(PULL0, RTI0, RTI1, RTI2, RTS0, RTS1, INDY0, INDX1)) {
      CI := 1.U
    }
    .otherwise {
      CI := 0.U
    }

    when(io.CE) {
      when(shift && (state in WRITE)) {
        C := CO
      }
      .elsewhen(state in RTI2) {
        C := DIMUX(0)
      }
      .elsewhen(~write_back & (state in DECODE)) {
        when(adc_sbc | shift | compare) {
          C := CO
        }
        .elsewhen(plp) {
          C := ADD(0)
        }
        .otherwise {
          when(sec) {
            C := 1.U
          }
          when(clc) {
            C := 0.U
          }
        }
      }

      when(state in WRITE) {
        Z := AZ
      }
      .elsewhen(state in RTI2) {
        Z := DIMUX(1)
      }
      .elsewhen(state in DECODE) {
        when(plp) {
          Z := ADD(1)
        }
          .elsewhen((load_reg & (regsel =/= SEL_S)) | compare | bit_ins) {
            Z := AZ
          }
      }

      when(state in WRITE) {
        N := AN
      }
        .elsewhen(state in RTI2) {
          N := DIMUX(7)
        }
        .elsewhen(state in DECODE) {
          when(plp) {
            N := ADD(7)
          }
            .elsewhen((load_reg & (regsel =/= SEL_S)) | compare) {
              N := AN
            }
        }
        .elsewhen((state in FETCH) && bit_ins) {
          N := DIMUX(7)
        }

      when(state in BRK3) {
        I := 1.U
      }
        .elsewhen(state in RTI2) {
          I := DIMUX(2)
        }
        .elsewhen(state in REG) {
          when(sei) {
            I := 1.U
          }
          when(cli) {
            I := 0.U
          }
        }
        .elsewhen(state in DECODE) {
          when(plp) {
            I := ADD(2)
          }
        }

      when(state in RTI2) {
        D := DIMUX(3)
      }
        .elsewhen(state in DECODE) {
          when(sed) {
            D := 1.U
          }
          when(cld) {
            D := 0.U
          }
          when(plp) {
            D := ADD(3)
          }
        }

      when(state in RTI2) {
        V := DIMUX(6)
      }
        .elsewhen(state in DECODE) {
          when(adc_sbc) {
            V := AV
          }
          when(clv) {
            V := 0.U
          }
          when(plp) {
            V := ADD(6)
          }
        }
        .elsewhen((state in FETCH) & bit_ins) {
          V := DIMUX(6)
        }
    }

    when(io.RDY & io.CE) {
      when(state in(PULL0, PUSH0)) {
        IRHOLD := DIMUX
        IRHOLD_valid := 1.B
      }
        .elsewhen(state in DECODE) {
          IRHOLD_valid := 0.B
        }
    }

    IR := Mux((io.IRQ & ~I) | NMI_edge, 0.U, Mux(IRHOLD_valid, IRHOLD, DIMUX))

    when(io.RDY) {
      DIHOLD := io.DI
    }

    DIMUX := Mux(!io.RDY || !RegNext(io.CE), DIHOLD, io.DI)


    when(io.RDY & io.CE) {
      when(state in DECODE) {
        state := Lookup(IR, state, IndexedSeq(
          BitPat("b0000_0000") -> BRK0,
          BitPat("b0010_0000") -> JSR0,
          BitPat("b0010_1100") -> ABS0, // BIT abs
          BitPat("b0100_0000") -> RTI0,
          BitPat("b0100_1100") -> JMP0,
          BitPat("b0110_0000") -> RTS0,
          BitPat("b0110_1100") -> JMPI0,
          BitPat("b0?00_1000") -> PUSH0,
          BitPat("b0?10_1000") -> PULL0,
          BitPat("b0??1_1000") -> REG, // CLC, SEC, CLI, SEI
          BitPat("b1??0_00?0") -> FETCH, // IMM
          BitPat("b1??0_1100") -> ABS0, // X/Y abs
          BitPat("b1???_1000") -> REG, // DEY, TYA, ...
          BitPat("b???0_0001") -> INDX0,
          BitPat("b???0_01??") -> ZP0,
          BitPat("b???0_1001") -> FETCH, // IMM
          BitPat("b???0_1101") -> ABS0, // even E column
          BitPat("b???0_1110") -> ABS0, // even E column
          BitPat("b???1_0000") -> BRA0, // odd 0 column
          BitPat("b???1_0001") -> INDY0, // odd 1 column
          BitPat("b???1_01??") -> ZPX0, // odd 4,5,6,7 columns
          BitPat("b???1_1001") -> ABSX0, // odd 9 column
          BitPat("b???1_11??") -> ABSX0, // odd C, D, E, F columns
          BitPat("b????_1010") -> REG // <shift> A, TXA, ...  NOP
        ))
      }
        .elsewhen(state in ZP0) {
          state := Mux(write_back, READ, FETCH)
        }.elsewhen(state in ZPX0) {
        state := ZPX1
      }
        .elsewhen(state in ZPX1) {
          state := Mux(write_back, READ, FETCH)
        }
        .elsewhen(state in ABS0) {
          state := ABS1
        }
        .elsewhen(state in ABS1) {
          state := Mux(write_back, READ, FETCH)
        }
        .elsewhen(state in ABSX0) {
          state := ABSX1
        }
        .elsewhen(state in ABSX1) {
          state := Mux(CO | store | write_back, ABSX2, FETCH)
        }
        .elsewhen(state in ABSX2) {
          state := Mux(write_back, READ, FETCH)
        }
        .elsewhen(state in INDX0) {
          state := INDX1
        }
        .elsewhen(state in INDX1) {
          state := INDX2
        }
        .elsewhen(state in INDX2) {
          state := INDX3
        }
        .elsewhen(state in INDX3) {
          state := FETCH
        }
        .elsewhen(state in INDY0) {
          state := INDY1
        }
        .elsewhen(state in INDY1) {
          state := INDY2
        }
        .elsewhen(state in INDY2) {
          state := Mux(CO | store, INDY3, FETCH)
        }
        .elsewhen(state in INDY3) {
          state := FETCH
        }
        .elsewhen(state in READ) {
          state := WRITE
        }
        .elsewhen(state in WRITE) {
          state := FETCH
        }
        .elsewhen(state in FETCH) {
          state := DECODE
        }
        .elsewhen(state in REG) {
          state := DECODE
        }
        .elsewhen(state in PUSH0) {
          state := PUSH1
        }
        .elsewhen(state in PUSH1) {
          state := DECODE
        }
        .elsewhen(state in PULL0) {
          state := PULL1
        }
        .elsewhen(state in PULL1) {
          state := PULL2
        }
        .elsewhen(state in PULL2) {
          state := DECODE
        }
        .elsewhen(state in JSR0) {
          state := JSR1
        }
        .elsewhen(state in JSR1) {
          state := JSR2
        }
        .elsewhen(state in JSR2) {
          state := JSR3
        }
        .elsewhen(state in JSR3) {
          state := FETCH
        }
        .elsewhen(state in RTI0) {
          state := RTI1
        }
        .elsewhen(state in RTI1) {
          state := RTI2
        }
        .elsewhen(state in RTI2) {
          state := RTI3
        }
        .elsewhen(state in RTI3) {
          state := RTI4
        }
        .elsewhen(state in RTI4) {
          state := DECODE
        }
        .elsewhen(state in RTS0) {
          state := RTS1
        }
        .elsewhen(state in RTS1) {
          state := RTS2
        }
        .elsewhen(state in RTS2) {
          state := RTS3
        }
        .elsewhen(state in RTS3) {
          state := FETCH
        }
        .elsewhen(state in BRA0) {
          state := Mux(cond_true, BRA1, DECODE)
        }
        .elsewhen(state in BRA1) {
          state := Mux(CO ^ backwards, BRA2, DECODE)
        }
        .elsewhen(state in BRA2) {
          state := DECODE
        }
        .elsewhen(state in JMP0) {
          state := JMP1
        }
        .elsewhen(state in JMP1) {
          state := DECODE
        }
        .elsewhen(state in JMPI0) {
          state := JMPI1
        }
        .elsewhen(state in JMPI1) {
          state := JMP0
        }
        .elsewhen(state in BRK0) {
          state := BRK1
        }
        .elsewhen(state in BRK1) {
          state := BRK2
        }
        .elsewhen(state in BRK2) {
          state := BRK3
        }
        .elsewhen(state in BRK3) {
          state := JMP0
        }
    }

    when(state in DECODE) {
      res := 0.B
    }

    when((state in DECODE) && io.RDY & io.CE) {
      load_reg := (IR in(BitPat("b0??0_1010"), BitPat("b0???_??01"), BitPat("b100?_10?0"), BitPat("b1010_???0"), BitPat("b1011_1010"), BitPat("b1011_?1?0"), BitPat("b1100_1010"), BitPat("b1?1?_??01"), BitPat("b???0_1000")))
    }

    when((state in DECODE) && io.RDY & io.CE) {
      when(IR in(BitPat("b1110_1000"), BitPat("b1100_1010"), BitPat("b101?_??10"))) {
        dst_reg := SEL_X
      }
        .elsewhen(IR in(BitPat("b0?00_1000"), BitPat("b1001_1010"))) {
          dst_reg := SEL_S
        }
        .elsewhen(IR in(BitPat("b1?00_1000"), BitPat("b101?_?100"), BitPat("b1010_?000"))) {
          dst_reg := SEL_Y
        }
        .otherwise {
          dst_reg := SEL_A
        }
    }

    when((state in DECODE) && io.RDY & io.CE) {
      when(IR in BitPat("b1011_1010")) {
        src_reg := SEL_S
      }
        .elsewhen(IR in(BitPat("b100?_?110"), BitPat("b100?_1?10"), BitPat("b1110_??00"), BitPat("b1100_1010"))) {
          src_reg := SEL_X
        }
        .elsewhen(IR in(BitPat("b100?_?100"), BitPat("b1001_1000"), BitPat("b1100_??00"), BitPat("b1?00_1000"))) {
          src_reg := SEL_Y
        }
        .otherwise {
          src_reg := SEL_A
        }
    }

    when((state in DECODE) && io.RDY & io.CE) {
      index_y := (IR in(BitPat("b???1_0001"), BitPat("b10?1_?110"), BitPat("b????_1001")))
    }

    when((state in DECODE) && io.RDY & io.CE) {
      store := (IR in(BitPat("b100?_?1?0"), BitPat("b100?_??01")))
    }

    when((state in DECODE) && io.RDY & io.CE) {
      write_back := (IR in(BitPat("b0???_?110"), BitPat("b11??_?110")))
    }

    when((state in DECODE) && io.RDY & io.CE) {
      load_only := (IR in BitPat("b101?_????"))
    }

    when((state in DECODE) && io.RDY & io.CE) {
      inc := (IR in(BitPat("b111??110"), BitPat("b11?01000")))
    }

    when((state in(DECODE, BRK0)) && io.RDY & io.CE) {
      adc_sbc := (IR in BitPat("b?11?_??01"))
    }

    when((state in(DECODE, BRK0)) && io.RDY & io.CE) {
      adc_bcd := Mux(IR in BitPat("b011?_??01"), D, 0.U)
    }

    when((state in DECODE) && io.RDY & io.CE) {
      shift := (IR in(BitPat("b0???_?110"), BitPat("b0??0_1010"))) // last BitPat changed from b0???_1010 to b0??0_1010 to fix unofficial nops.
    }

    when((state in DECODE) && io.RDY & io.CE) {
      compare := (IR in(BitPat("b11?0_0?00"), BitPat("b11?0_1100"), BitPat("b110?_??01")))
    }

    when((state in DECODE) && io.RDY & io.CE) {
      shift_right := (IR in BitPat("b01??_??10"))
    }

    when((state in DECODE) && io.RDY & io.CE) {
      rotate := (IR in(BitPat("b0?1?_1010"), BitPat("b0?1?_?110")))
    }

    when((state in DECODE) && io.RDY & io.CE) {
      when(IR in BitPat("b00??_??10")) {
        op := OP_ROL
      }
        .elsewhen(IR in BitPat("b0010_?100")) {
          op := OP_AND
        }
        .elsewhen(IR in BitPat("b01??_??10")) {
          op := OP_A
        }
        .elsewhen(IR in(BitPat("b1000_1000"), BitPat("b1100_1010"), BitPat("b110?_?110"), BitPat("b11??_??01"), BitPat("b11?0_0?00"), BitPat("b11?0_1100"))) {
          op := OP_SUB
        }
        .elsewhen(IR in(BitPat("b010?_??01"), BitPat("b00??_??01"))) {
          op := Cat(3.U(2.W), IR(6, 5))
        }
        .otherwise {
          op := OP_ADD
        }
    }

    when((state in DECODE) && io.RDY & io.CE) {
      bit_ins := IR in BitPat("b0010_?100")
    }

    when((state in DECODE) && io.RDY & io.CE) {
      php := (IR === 0x08.U)
      clc := (IR === 0x18.U)
      plp := (IR === 0x28.U)
      sec := (IR === 0x38.U)
      cli := (IR === 0x58.U)
      sei := (IR === 0x78.U)
      clv := (IR === 0xb8.U)
      cld := (IR === 0xd8.U)
      sed := (IR === 0xf8.U)
      brk := (IR === 0x00.U)
    }

    when(io.RDY & io.CE) {
      cond_code := IR(7, 5)
    }

    cond_true := MuxLookup(cond_code, 0.U, IndexedSeq(
      0.U -> ~N,
      1.U -> N,
      2.U -> ~V,
      3.U -> V,
      4.U -> ~C,
      5.U -> C,
      6.U -> ~Z,
      7.U -> Z
    ))

    val NMI_1 = RegNext(io.NMI,0.B)

    when(NMI_edge && (state in BRK3)) {
      NMI_edge := 0.B
    }
    .elsewhen(io.NMI & ~NMI_1) {
      NMI_edge := 1.B
    }
}
