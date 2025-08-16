package tcam

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheArbiter}
import freechips.rocketchip.util.DecoupledHelper
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import freechips.rocketchip.tilelink._

class TCAMRoCC(opcodes: OpcodeSet, tcamParams: TCAMParams)(implicit p: Parameters)
    extends LazyRoCC(opcodes) {
  override lazy val module = new TCAMRoCCModule(this, tcamParams)
}

 class TCAMRoCCModule(outer: TCAMRoCC, tcamParams: TCAMParams)
    extends LazyRoCCModuleImp(outer) {
  val cmd = io.cmd
  val tcam = Module(new TCAMBlackBox(tcamParams))
  
  
   when(io.cmd.fire) {
    printf("TCAMRoCC DEBUG: FIRE opcode=0x%x funct=0x%x rd=%d\n",
      cmd.bits.inst.opcode, cmd.bits.inst.funct, cmd.bits.inst.rd)
  }
  
  val sIdle :: sExec :: sProbe :: sResp :: Nil = Enum(4)
  val state = RegInit(sIdle)

  // Latched fields
  val rdReg     = Reg(UInt(5.W))
  val wmaskReg  = Reg(UInt(4.W))
  val addrReg   = Reg(UInt(28.W))
  val wdataReg  = Reg(UInt(32.W))
  val inwebReg  = Reg(Bool()) 
  val incsbReg  = Reg(Bool()) 
  val respData  = Reg(UInt(64.W))
  val lastPma   = Reg(UInt(6.W))
  val probeCount = RegInit(0.U(4.W))
  val lastPmaValid = RegInit(false.B)

  // Defaults
  io.cmd.ready := (state === sIdle)
  io.busy := (state =/= sIdle)

  io.resp.valid := (state === sResp)
  io.resp.bits.rd := rdReg
  io.resp.bits.data := respData

  // Local wires with safe defaults to ensure full initialization
  val tcam_in_wmask = Wire(UInt(4.W));  tcam_in_wmask := 0.U
  val tcam_in_addr  = Wire(UInt(28.W)); tcam_in_addr  := 0.U
  val tcam_in_wdata = Wire(UInt(32.W)); tcam_in_wdata := 0.U
  val tcam_in_web   = Wire(Bool());     tcam_in_web   := true.B  
  val tcam_in_csb   = Wire(Bool());     tcam_in_csb   := true.B  

  switch(state) {
    is(sIdle) {
      when(cmd.fire) {
        // Extract and latch fields
        rdReg    := cmd.bits.inst.rd
        wmaskReg := cmd.bits.rs1(31, 28)
        addrReg  := cmd.bits.rs1(27, 0)
        wdataReg := cmd.bits.rs2(31, 0)
        inwebReg := cmd.bits.inst.funct(1)
        incsbReg := cmd.bits.inst.funct(0)

        // Extra trace
        printf("TCAMRoCC: LATCH rs1=0x%x rs2=0x%x funct=0x%x wmask=0x%x addr=0x%x inweb=%b incsb=%b\n",
          cmd.bits.rs1, cmd.bits.rs2, cmd.bits.inst.funct, cmd.bits.rs1(31,28), cmd.bits.rs1(27,0), cmd.bits.inst.funct(1), cmd.bits.inst.funct(0))

        state := sExec
      }
    }

    is(sExec) {
      // Map RoCC-level active-high enables to TCAM active-low signals
      val op = Cat(inwebReg, incsbReg)

      // Common data/addr/wmask
      tcam_in_wmask := wmaskReg
      tcam_in_addr  := addrReg
      tcam_in_wdata := wdataReg

      switch(op) {
        is("b00".U) { // Write
          tcam_in_web := false.B // assert write (active-low)
          tcam_in_csb := false.B // enable chip (active-low)
          printf("TCAMRoCC: EXEC Write wmask=0x%x addr=0x%x wdata=0x%x\n", wmaskReg, addrReg, wdataReg)
        }
        is("b01".U) { // inactive
          tcam_in_web := false.B  // no write
          tcam_in_csb := true.B // enable chip
          printf("TCAMRoCC: EXEC nothing  wmask=0x%x addr=0x%x\n", wmaskReg, addrReg)
        }
        is("b10".U) { // Search
          tcam_in_web := true.B  // no write
          tcam_in_csb := false.B // enable chip
          printf("TCAMRoCC: EXEC Search wmask=0x%x addr=0x%x wdata=0x%x\n", wmaskReg, addrReg, wdataReg)
          probeCount := 0.U
          lastPmaValid := false.B
        }
        is("b11".U) { // Reserved/No-op
          tcam_in_web := true.B
          tcam_in_csb := true.B
          printf("TCAMRoCC: EXEC Status\n")
        }
      }

      
      when(op === "b10".U) { // search -> probe for result
        state := sProbe
      }.elsewhen(op === "b11".U) { // status -> respond immediately with lastPma
        respData := Cat(0.U(58.W), lastPma)
        state := sResp
      }.otherwise { // read/write -> respond this cycle
        respData := tcam.io.out_pma
        state := sResp
      }
    }

    is(sProbe) { // hold signals and sample up to a few cycles
      // Hold TCAM selected and inputs stable
      tcam_in_wmask := wmaskReg
      tcam_in_addr  := addrReg
      tcam_in_wdata := wdataReg
      tcam_in_web   := true.B
      tcam_in_csb   := false.B

      // Sample current PMA
      printf("TCAMRoCC: PROBE[%x] out_pma=0x%x addr=0x%x\n", probeCount, tcam.io.out_pma, addrReg)
      val gotValid = tcam.io.out_pma.orR
      when(gotValid || probeCount === 7.U) {
        lastPma := tcam.io.out_pma
        lastPmaValid := gotValid
        respData := Cat(0.U(58.W), tcam.io.out_pma)
        state := sResp
      }.otherwise {
        probeCount := probeCount + 1.U
      }
    }

    is(sResp) {
      // Print match status only when responding to the status op (funct=3)
      when(inwebReg && incsbReg) {
        when(lastPmaValid) {
          printf("TCAM match status: 0x%x\n", lastPma)
        }.otherwise {
          printf("TCAM match status: (no valid result)\n")
        }
      }
        when(io.resp.ready) {
        state := sIdle
      }
    }
      
  }

  // Single connection to BlackBox after all assignments
  tcam.io.in_clk   := clock
  tcam.io.in_wmask := tcam_in_wmask
  tcam.io.in_addr  := tcam_in_addr
  tcam.io.in_wdata := tcam_in_wdata
  tcam.io.in_web   := tcam_in_web
  tcam.io.in_csb   := tcam_in_csb
}

