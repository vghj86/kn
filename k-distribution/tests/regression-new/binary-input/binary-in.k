// Copyright (c) K Team. All Rights Reserved.

module BINARY-IN-SYNTAX
  imports INT-SYNTAX
  imports ML-SYNTAX

  syntax Pgm ::= init ( Int ) [macro]
  rule init(0) => { 3 #Equals 4 }
  rule init(1) => { 3 #Equals 3 }
endmodule

module BINARY-IN
  imports BINARY-IN-SYNTAX
  configuration <k color="green"> $PGM:Pgm </k>
endmodule
