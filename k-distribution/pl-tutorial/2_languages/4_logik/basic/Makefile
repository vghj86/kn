MAKEFILE_PATH := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
DEF=logik
EXT=logik	
KOMPILE_BACKEND?=java
KRUN_FLAGS=--search --pattern "<solution> Solution:Map </solution>"

include $(MAKEFILE_PATH)/../../../find-k.mak
include ${K_HOME}/include/kframework/ktest.mak

tests/reverse-slow-2.logik: kompile
	cat $@.in 2>/dev/null | $(KRUN_OR_LEGACY) $@ --definition $(DEF)-kompiled $(KRUN_FLAGS) --bound 1 $(CHECK) $@.out
tests/reverse-slow-palindromes-1.logik: kompile
	cat $@.in 2>/dev/null | $(KRUN_OR_LEGACY) $@ --definition $(DEF)-kompiled $(KRUN_FLAGS) --bound 5 $(CHECK) $@.out
tests/append-4.logik: kompile
	cat $@.in 2>/dev/null | $(KRUN_OR_LEGACY) $@ --definition $(DEF)-kompiled $(KRUN_FLAGS) --bound 5 $(CHECK) $@.out
tests/reverse-fast-2.logik: kompile
	cat $@.in 2>/dev/null | $(KRUN_OR_LEGACY) $@ --definition $(DEF)-kompiled $(KRUN_FLAGS) --bound 1 $(CHECK) $@.out
tests/reverse-fast-palindromes-1.logik: kompile
	cat $@.in 2>/dev/null | $(KRUN_OR_LEGACY) $@ --definition $(DEF)-kompiled $(KRUN_FLAGS) --bound 5 $(CHECK) $@.out
	
