MAKEFILE_PATH := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
DEF=fun-untyped
EXT=fun
TESTDIR?=../../programs
RESULTDIR=tests
KOMPILE_FLAGS?=--enable-search
KRUN_FLAGS?=--pattern "<k> V:K </k>"

%/references-5.fun: kompile
	cat tests/references-5.fun.in 2>/dev/null | $(KRUN_OR_LEGACY) $@ --definition $(DEF)-kompiled --search --pattern "<k> V:K </k>" $(CHECK) tests/references-5.fun.out

include $(MAKEFILE_PATH)/../../../../find-k.mak
include ${K_HOME}/include/kframework/ktest.mak
