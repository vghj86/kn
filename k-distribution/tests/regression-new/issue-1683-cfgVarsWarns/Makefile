DEF=test
EXT=test
TESTDIR=.
KOMPILE_FLAGS=--syntax-module TEST
KOMPILE_BACKEND=haskell
KRUN_FLAGS=-cA=.K

include ../../../include/kframework/ktest.mak
%.$(EXT): kompile
	$(KRUN_OR_LEGACY) $@ $(KRUN_FLAGS) $(DEBUG) --definition $(DEF)-kompiled $(CONSIDER_ERRORS) $(REMOVE_PATHS) $(CHECK) $@.out
