package tcam

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._
import org.chipsalliance.cde.config.{Parameters, Field}
import java.io.{File, FileWriter, PrintWriter}
import scala.sys.process._

case class TCAMTableConfig(
  queryStrLen: Int,    // Total query string length
  subStrLen: Int,      // Length of each substring
  totalSubStr: Int,    // Number of substrings
  potMatchAddr: Int    // Number of potential match addresses
)

case class TCAMParams(
  width: Int = 32,           // Data width
  keyWidth: Int = 28,        // Search key width  
  entries: Int = 64,         // Number of TCAM entries
  sramDepth: Int = 256,      // SRAM depth
  sramWidth: Int = 32,       // SRAM width
  priorityEncoder: Boolean = true,  // Enable priority encoding
  address: BigInt = 0x4000,  // MMIO base address
  useAXI4: Boolean = false,  // Use AXI4 instead of TileLink
  tableConfig: Option[TCAMTableConfig] = None  // Specific table configuration
)

case object TCAMKey extends Field[Option[TCAMParams]](None)

class TCAMIO extends Bundle {
  val in_clk = Input(Clock())
  val in_csb = Input(Bool())
  val in_web = Input(Bool())
  val in_wmask = Input(UInt(4.W))
  val in_addr = Input(UInt(28.W))
  val in_wdata = Input(UInt(32.W))
  val out_pma = Output(UInt(5.W))
}

// Blackbox wrapper for generated RTL
class TCAMBlackBox(params: TCAMParams) extends BlackBox with HasBlackBoxPath {
  val io = IO(new TCAMIO)  
  
  // Get table configuration for file names
  val tableConfig = params.tableConfig.getOrElse(TCAMTableConfig(
    queryStrLen = params.keyWidth,
    subStrLen = params.keyWidth / 4,
    totalSubStr = 4,
    potMatchAddr = params.entries
  ))
  
  // Add paths to generated files
  val chipyardDir = System.getProperty("user.dir")
  
  val isTCAM32x28 = params.keyWidth == 28 && params.entries == 32 && 
                     params.tableConfig.exists(tc => tc.queryStrLen == 28 && tc.potMatchAddr == 32)
                     
  if (isTCAM32x28) {
  val tcamDir = s"$chipyardDir/generators/tcam/src/main/resources/vsrc/tcam32x28"
  addPath(s"$tcamDir/TCAMBlackBox.sv")
  addPath(s"$tcamDir/sky130_sram_1kbyte_1rw1r_32x256_8.sv")
  } else {
  val tcamDir = s"$chipyardDir/generators/tcam/src/main/resources/vsrc/tcam64x28"
  addPath(s"$tcamDir/TCAMBlackBox.sv")
  addPath(s"$tcamDir/sky130_sram_1kbyte_1rw1r_32x256_8.sv")
  addPath(s"$tcamDir/and_gate.sv")
  addPath(s"$tcamDir/tcam_7x64.sv")
  addPath(s"$tcamDir/priority_encoder_64x6.sv")
  }
}

// TileLink wrapper for MMIO integration
class TCAMTL(params: TCAMParams, beatBytes: Int)(implicit p: Parameters) 
    extends ClockSinkDomain(ClockSinkParameters())(p) {
  
  val device = new SimpleDevice("tcam", Seq("ucbbar,tcam"))
  val node = TLRegisterNode(
    Seq(AddressSet(params.address, 4096-1)), 
    device, 
    "reg/control", 
    beatBytes=beatBytes
  )
  
  override lazy val module = new TCAMImpl
  
  class TCAMImpl extends Impl {
    withClockAndReset(clock, reset) {
      val tcam = Module(new TCAMBlackBox(params))
      
      // Get table configuration for output width
      val tableConfig = params.tableConfig.getOrElse(TCAMTableConfig(
        queryStrLen = params.keyWidth,
        subStrLen = params.keyWidth / 4,
        totalSubStr = 4,
        potMatchAddr = params.entries
      ))
      val outputWidth = log2Ceil(tableConfig.potMatchAddr)
      
      // MMIO registers
      val status = RegInit(0.U(outputWidth.W))     
      val control = RegInit(0.U(8.W))    
      val writeData = RegInit(0.U(32.W))  
      val address = RegInit(0.U(28.W))      
      
      // Connect TCAM interface
      tcam.io.in_clk := clock
      tcam.io.in_csb := ~control(0)
      tcam.io.in_web := ~control(1)
      tcam.io.in_wmask := control(7, 4)
      tcam.io.in_addr := address
      tcam.io.in_wdata := writeData
      status := tcam.io.out_pma
      
      // MMIO register map
      node.regmap(
        0x00 -> Seq(RegField.r(outputWidth, status)),
        0x04 -> Seq(RegField(8, control)),
        0x08 -> Seq(RegField(32, writeData)),
        0x0C -> Seq(RegField(28, address)),
      )
    }
    
    private def log2Ceil(x: Int): Int = {
      if (x <= 1) 1 else 32 - Integer.numberOfLeadingZeros(x - 1)
    }
  }
}

// AXI4 wrapper for MMIO integration
class TCAMAXI4(params: TCAMParams, beatBytes: Int)(implicit p: Parameters) 
    extends ClockSinkDomain(ClockSinkParameters())(p) {
  
  val node = AXI4RegisterNode(
    AddressSet(params.address, 4096-1), 
    beatBytes=beatBytes
  )
  
  override lazy val module = new TCAMImpl
  
  class TCAMImpl extends Impl {
    withClockAndReset(clock, reset) {
      val tcam = Module(new TCAMBlackBox(params))
      
      // Get table configuration for output width
      val tableConfig = params.tableConfig.getOrElse(TCAMTableConfig(
        queryStrLen = params.keyWidth,
        subStrLen = params.keyWidth / 4,
        totalSubStr = 4,
        potMatchAddr = params.entries
      ))
      val outputWidth = log2Ceil(tableConfig.potMatchAddr)
      
      // MMIO registers
      val status = RegInit(0.U(outputWidth.W))     
      val control = RegInit(0.U(8.W))    
      val writeData = RegInit(0.U(32.W))  
      val address = RegInit(0.U(28.W))      
      
      // Connect TCAM interface
      tcam.io.in_clk := clock
      tcam.io.in_csb := ~control(0)
      tcam.io.in_web := ~control(1)
      tcam.io.in_wmask := control(7, 4)
      tcam.io.in_addr := address
      tcam.io.in_wdata := writeData
      status := tcam.io.out_pma
      
      // MMIO register map
      node.regmap(
        0x00 -> Seq(RegField.r(outputWidth, status)),
        0x04 -> Seq(RegField(8, control)),
        0x08 -> Seq(RegField(32, writeData)),
        0x0C -> Seq(RegField(28, address)),
      )
    }
    
    private def log2Ceil(x: Int): Int = {
      if (x <= 1) 1 else 32 - Integer.numberOfLeadingZeros(x - 1)
    }
  }
}

// Trait for subsystem integration
trait CanHavePeripheryTCAM { this: BaseSubsystem =>
  private val portName = "tcam"
  private val pbus = locateTLBusWrapper(PBUS)
  
  p(TCAMKey).map { params =>
    val tcam = if (params.useAXI4) {
      val tcam = LazyModule(new TCAMAXI4(params, pbus.beatBytes)(p))
      tcam.clockNode := pbus.fixedClockNode
      pbus.coupleTo(portName) {
        AXI4InwardClockCrossingHelper("tcam_crossing", tcam, tcam.node)(SynchronousCrossing()) :=
        AXI4Buffer() :=
        TLToAXI4() :=
        TLFragmenter(pbus.beatBytes, pbus.blockBytes, holdFirstDeny = true) := _
      }
      tcam
    } else {
      val tcam = LazyModule(new TCAMTL(params, pbus.beatBytes)(p))
      tcam.clockNode := pbus.fixedClockNode
      pbus.coupleTo(portName) {
        TLInwardClockCrossingHelper("tcam_crossing", tcam, tcam.node)(SynchronousCrossing()) :=
        TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _
      }
      tcam
    }
  }
} 
