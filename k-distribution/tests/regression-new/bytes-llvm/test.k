// Copyright (c) K Team. All Rights Reserved.
module TEST
  imports DOMAINS
  imports BYTES

configuration <k> $PGM:K </k>

syntax Int ::= "(" Int ")" [bracket]
syntax Bytes ::= "(" Bytes ")" [bracket]
syntax String ::= "(" String ")" [bracket]

endmodule
