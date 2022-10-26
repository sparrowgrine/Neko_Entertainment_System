package ee.catgirl.nes.util

import chisel3._
import chisel3.util.HasBlackBoxInline
import firrtl.ir.Type
import treadle.{ScalaBlackBox, ScalaBlackBoxFactory}
import treadle.executable.{PositiveEdge, Transition}
import chisel3.experimental._
import chisel3.util._
import firrtl.ir.Type
import org.scalacheck.Prop.Exception
import treadle._

import scala.collection.mutable

/*
 * This is inspired by Angie Wang's excellent UIntLut2D
 * The main difference comes from the fact that Xilinx can infer BRAMs for ROMs.
 * It can do single and dual port BRAMs. Xilinx publishes guidelines for how to write
 * verilog/vhdl in a way that allows the tool to infer BRAMs.
 * The basic idea is that it should be written using a case statement and the output should be registered
 */

class AsyncROM(val blackboxName: String, val table: Seq[BigInt], val widthOverride: Option[Int] = None)
  extends Module {
  val dataWidth = AsyncROMBlackBox.dataWidth(table, widthOverride)

  val addrWidth = AsyncROMBlackBox.addrWidth(table)

  val io = IO(new AsyncROMIO(addrWidth=addrWidth, dataWidth=dataWidth))

  val rom = Module(new AsyncROMBlackBox(blackboxName, table, widthOverride))

  rom.io.clock := clock
  rom.io.addr  := io.addr
  io.data      := rom.io.data
}

trait HasBlackBoxClock {
  val clock = Input(Clock())
}

class AsyncROMIO(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val addr  = Input(UInt(addrWidth.W))
  val data  = Output(UInt(dataWidth.W))
}

class AsyncROMBlackBox(blackboxName: String, table: Seq[BigInt], widthOverride: Option[Int] = None)
  extends BlackBox with HasBlackBoxInline {
  val dataWidth = SyncROMBlackBox.dataWidth(table, widthOverride)

  val addrWidth = SyncROMBlackBox.addrWidth(table)

  val io = IO(new SyncROMIO(addrWidth=addrWidth, dataWidth = dataWidth) with HasBlackBoxClock)

  override def desiredName: String = blackboxName

  def tableEntry2InitStr(value: BigInt, addr: BigInt): String = {
    s"      mem[$addrWidth'b${addr.toString(2)}] = $dataWidth'h${value.toString(16)};\n"
  }

  val tableStringBuilder = new mutable.StringBuilder(20*table.length)
  table.zipWithIndex.foreach { case (t, i) => tableStringBuilder ++= tableEntry2InitStr(t, BigInt(i))}

  val verilog : String =
    s"""
       |module $name(
       |  input clock,
       |  input  [${(addrWidth - 1).max(0)}:0] addr,
       |  output [${(dataWidth - 1).max(0)}:0] data
       |);
       |  reg [${(dataWidth - 1).max(0)}:0] mem [${table.length-1}:0];
       |  assign data = mem[addr];
       |  initial begin
       |  ${tableStringBuilder.mkString}
       |  end
       |endmodule
     """.stripMargin

  setInline(s"$name.v", verilog)
  AsyncROMBlackBox.interpreterMap.update(name, (table, dataWidth))
}

object AsyncROMBlackBox {
  def addrWidth(table: Seq[BigInt]): Int = {
    BigInt(table.length - 1).bitLength
  }
  def dataWidth(table: Seq[BigInt], widthOverride: Option[Int]): Int = {
    val w = widthOverride.getOrElse(table.map{_.bitLength}.max)
    require(w >= table.map{_.bitLength}.max, "width too small for table")
    w
  }
  private [util] val interpreterMap = mutable.Map[String, (Seq[BigInt], Int)]()
}

// implementation for firrtl interpreter
class AsyncROMBlackBoxImplementation(val name: String, val table: Seq[BigInt], dataWidth: Int, default: BigInt = 0)  extends ScalaBlackBox {

  var currentCycleAddr: BigInt = BigInt(0)

  override def clockChange(transition: Transition, clockName: String = ""): Unit = {
  }

  override def inputChanged(name: String, value: BigInt): Unit = {
    currentCycleAddr = value

  }
  override def getOutput(inputValues: Seq[BigInt], tpe: Type, outputName: String): BigInt = {
    val tableValue = if (currentCycleAddr.toInt < table.length) {
      table(currentCycleAddr.toInt)
    } else {
      default
    }
    tableValue
  }

  override def outputDependencies(outputName: String) = Seq("addr")
}

class AsyncROMBlackBoxFactory extends ScalaBlackBoxFactory {
  override def createInstance(instanceName: String, blackBoxName: String) : Option[ScalaBlackBox]  = {
    AsyncROMBlackBox.interpreterMap.get(blackBoxName).map {
      case (table, dataWidth) => add(new AsyncROMBlackBoxImplementation(instanceName, table, dataWidth))
    }
  }
}