// Copyright (c) K Team. All Rights Reserved.
module A-SYNTAX
  imports DOMAINS-SYNTAX
endmodule

module A
  imports DOMAINS
  rule I:Int => I in_keys(.Map)
endmodule
