module TCAMBlackBox (
    input   logic           in_clk,
    input   logic           in_csb,
    input   logic           in_web,
    input   logic   [3:0]   in_wmask,
    input   logic   [27:0]  in_addr,
    input   logic   [31:0]  in_wdata,
    output  logic   [5:0]   out_pma
);

//////////////////////////////////////////////////////
// Read Address/Search Query bit division           //
//////////////////////////////////////////////////////

    logic   [7:0]   raddr_vtb1; // tcam virtual block 1
    logic   [6:0]   raddr_vtb2; // tcam virtual block 2
    logic   [7:0]   raddr_vtb3; // tcam virtual block 3
    logic   [6:0]   raddr_vtb4; // tcam virtual block 4
    logic           c_hi;

    always_comb begin : search_query_bits
        raddr_vtb1 = {1'b0, in_addr[27:21]};
        // raddr_vtb2 = in_addr[20:14];
        raddr_vtb3 = {1'b0, in_addr[13:7]};
        // raddr_vtb4 = in_addr[6:0];
        c_hi = 1'b1;
    end

//////////////////////////////////////////////////////
// write address bit division                       //
//////////////////////////////////////////////////////

    logic           aw_select;
    logic           we;
    logic   [7:0]   aw_sb1;     // sram block 1
    logic   [7:0]   aw_sb2;     // sram block 2
    logic   [7:0]   addr1;
    logic   [7:0]   addr2;

    always_comb begin : write_address_demux
        aw_select   = in_addr[8];
        we          = ~in_web;
        aw_sb1      = {8{we}} & {8{(~aw_select)}} & in_addr[7:0];
        aw_sb2      = {8{we}} & {8{aw_select}} & in_addr[7:0];
        addr1       = in_web ? raddr_vtb1 : aw_sb1;
        addr2       = in_web ? raddr_vtb3 : aw_sb2;
    end

//////////////////////////////////////////////////////
//	            write masking                       //
//////////////////////////////////////////////////////

    logic   [3:0]   wmask1;
    logic   [3:0]   wmask2;

    always_comb begin
        wmask1 = in_wmask & {4{~aw_select}};
        wmask2 = in_wmask & {4{aw_select}};
    end

//////////////////////////////////////////////////////
// Read/Write clock gating                          //
////////////////////////////////////////////////////// 

    logic vtb_clk;
    always_comb begin
        vtb_clk = (in_web) ? in_clk : 1'b0;
    end

//////////////////////////////////////////////////////
// Output logic                                     //
////////////////////////////////////////////////////// 

    logic   [31:0]  vtb_out1;
    logic   [31:0]  vtb_out2;
    logic   [31:0]  vtb_out3;
    logic   [31:0]  vtb_out4;
    logic   [31:0]  p_out;
    logic   [5:0]   rdata;
    logic   [31:0]  vtb_out1_and_vtb_out2;
    logic   [31:0]  vtb_out3_and_vtb_out4;

    always_comb begin
        vtb_out1_and_vtb_out2 = vtb_out1 & vtb_out2;
        vtb_out3_and_vtb_out4 = vtb_out3 & vtb_out4;
        p_out = vtb_out1_and_vtb_out2 & vtb_out3_and_vtb_out4;
    end

//////////////////////////////////////////////////////
// Priority Encoding                                //
////////////////////////////////////////////////////// 

    always @(p_out) begin
        if (p_out[0])
            rdata = 6'd1;
        else if (p_out[1])  rdata = 6'd2;
        else if (p_out[2])  rdata = 6'd3;
        else if (p_out[3])  rdata = 6'd4;
        else if (p_out[4])  rdata = 6'd5;
        else if (p_out[5])  rdata = 6'd6;
        else if (p_out[6])  rdata = 6'd7;
        else if (p_out[7])  rdata = 6'd8;
        else if (p_out[8])  rdata = 6'd9; 
        else if (p_out[9])  rdata = 6'd10; 
        else if (p_out[10]) rdata = 6'd11; 
        else if (p_out[11]) rdata = 6'd12; 
        else if (p_out[12]) rdata = 6'd13; 
        else if (p_out[13]) rdata = 6'd14; 
        else if (p_out[14]) rdata = 6'd15; 
        else if (p_out[15]) rdata = 6'd16; 
        else if (p_out[16]) rdata = 6'd17;
        else if (p_out[17]) rdata = 6'd18;
        else if (p_out[18]) rdata = 6'd19;
        else if (p_out[19]) rdata = 6'd20;
        else if (p_out[20]) rdata = 6'd21;
        else if (p_out[21]) rdata = 6'd22;
        else if (p_out[22]) rdata = 6'd23;
        else if (p_out[23]) rdata = 6'd24;
        else if (p_out[24]) rdata = 6'd25;
        else if (p_out[25]) rdata = 6'd26;
        else if (p_out[26]) rdata = 6'd27;
        else if (p_out[27]) rdata = 6'd28;
        else if (p_out[28]) rdata = 6'd29;
        else if (p_out[29]) rdata = 6'd30;
        else if (p_out[30]) rdata = 6'd31;
        else if (p_out[31]) rdata = 6'd32;
        else
            rdata = 6'd0;
    end

    always_comb begin
        out_pma = rdata;
    end

//////////////////////////////////////////////////////
// SRAM/Virtual TCAM blocks                         //
////////////////////////////////////////////////////// 

    sky130_sram_1kbyte_1rw1r_32x256_8 vtb_sb1(
        `ifdef USE_POWER_PINS
        .vccd1  (),
        .vssd1  (),
        `endif
        // Port 0: RW
        .clk0   (in_clk),
        .csb0   (in_csb),
        .web0   (in_web),
        .wmask0 (wmask1),
        .addr0  (addr1),
        .din0   (in_wdata),
        .dout0  (vtb_out1),
        // Port 1: R
        .clk1   (vtb_clk),
        .csb1   (in_csb),
        .addr1  ({c_hi,in_addr[20:14]}),
        .dout1  (vtb_out2)
    );

    sky130_sram_1kbyte_1rw1r_32x256_8 vtb_sb2(
        `ifdef USE_POWER_PINS
        .vccd1  (),
        .vssd1  (),
        `endif
        // Port 0: RW
        .clk0   (in_clk),
        .csb0   (in_csb),
        .web0   (in_web),
        .wmask0 (wmask2),
        .addr0  (addr2),
        .din0   (in_wdata),
        .dout0  (vtb_out3),
        // Port 1: R
        .clk1   (vtb_clk),
        .csb1   (in_csb),
        .addr1  ({c_hi,in_addr[6:0]}),
        .dout1  (vtb_out4)
    );

endmodule

