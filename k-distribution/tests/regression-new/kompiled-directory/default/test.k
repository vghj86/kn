// Copyright (c) K Team. All Rights Reserved.
module TEST
  imports DOMAINS
  imports K-REFLECTION

  syntax K ::= Int

  rule _:Int => #kompiledDirectory()
endmodule
