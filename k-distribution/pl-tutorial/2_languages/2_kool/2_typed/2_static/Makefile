MAKEFILE_PATH := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
DEF=kool-typed-static
EXT=kool
KOMPILE_FLAGS=--enable-search
KRUN_FLAGS=--output none
TESTDIR?=../programs
RESULTDIR=tests

%/cycle.kool: kompile
	cat tests/cycle.kool.in 2>/dev/null | $(KRUN_OR_LEGACY) $@ --definition $(DEF)-kompiled --search --pattern '<output> ListItem(#ostream(1)) ListItem("off") ListItem(#buffer(S:String)) </output>' $(CHECK) tests/cycle.kool.out

%/return-object.kool: kompile
	true

include $(MAKEFILE_PATH)/../../../../find-k.mak
include ${K_HOME}/include/kframework/ktest.mak
