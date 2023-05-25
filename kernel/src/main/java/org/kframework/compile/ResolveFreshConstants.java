// Copyright (c) K Team. All Rights Reserved.
package org.kframework.compile;

import org.kframework.attributes.Att;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.KLabels;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Context;
import org.kframework.definition.Definition;
import org.kframework.definition.Import;
import org.kframework.definition.Module;
import org.kframework.definition.NonTerminal;
import org.kframework.definition.Production;
import org.kframework.definition.ProductionItem;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;
import org.kframework.kore.TransformK;
import org.kframework.kore.VisitK;
import org.kframework.parser.inner.ParseInModule;
import org.kframework.parser.inner.RuleGrammarGenerator;
import org.kframework.parser.outer.Outer;
import org.kframework.utils.StringUtil;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.errorsystem.KEMException;
import scala.collection.Set;
import scala.Option;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

public class ResolveFreshConstants {

    private final Definition def;
    private final FileUtil files;
    private Module m;
    private java.util.Set<KVariable> freshVars = new HashSet<>();
    private Map<KVariable, Integer> offsets = new HashMap<>();
    private final String manualTopCell;
    private final Boolean pedanticAttributes;

    private void reset() {
        freshVars.clear();
        offsets.clear();
    }

    private Rule resolve(Rule rule) {
        reset();
        analyze(rule.body());
        analyze(rule.requires());
        analyze(rule.ensures());
        finishAnalysis();
        Rule withFresh = Rule(
                addFreshCell(transform(rule.body())),
                transform(rule.requires()),
                transform(rule.ensures()),
                rule.att());
        if (rule.att().contains(Att.INITIALIZER())) {
            K left = RewriteToTop.toLeft(withFresh.body());
            if (left instanceof KApply) {
                KApply kapp = (KApply) left;
                if (kapp.klabel().equals(KLabels.INIT_GENERATED_TOP_CELL)) {
                    KApply right = (KApply)RewriteToTop.toRight(withFresh.body());
                    KApply cells = (KApply)right.items().get(1);
                    List<K> items = new ArrayList<>(cells.items());
                    items.add(KApply(KLabels.INIT_GENERATED_COUNTER_CELL));
                    KApply newCells = KApply(cells.klabel(), immutable(items));
                    List<K> rightItems = new ArrayList<>(right.items());
                    rightItems.set(1, newCells);
                    return Rule(
                            KRewrite(left, KApply(right.klabel(), immutable(rightItems))),
                            withFresh.requires(),
                            withFresh.ensures(),
                            withFresh.att());
                }
            }
        }
        K left = RewriteToTop.toLeft(rule.body());
        if (left instanceof KApply) {
            KApply kapp = (KApply)left;
            if (kapp.klabel().name().equals("#withConfig")) {
                left = kapp.items().get(0);
            }
            if (left instanceof KApply) {
                kapp = (KApply)left;
                if (m.attributesFor().get(kapp.klabel()).getOrElse(() -> Att()).contains(Att.FUNCTION())) {
                    return rule;
                }
            }
        }
        return withFresh;
    }

    private void analyze(K term) {
        new VisitK() {
            @Override
            public void apply(KVariable k) {
                if (k.name().startsWith("!")) {
                    freshVars.add(k);
                }
                super.apply(k);
            }
        }.apply(term);
    }

    private void finishAnalysis() {
        int i = 0;
        for (KVariable v : freshVars) {
            offsets.put(v, i++);
        }
    }

    private static KVariable FRESH = KVariable("#Fresh", Att.empty().add(Sort.class, Sorts.Int()));

    private K transform(K term) {
        return new TransformK() {
            @Override
            public K apply(KVariable k) {
                if (freshVars.contains(k)) {
                    Optional<Sort> s = k.att().getOptional(Sort.class);
                    if (!s.isPresent()) {
                        throw KEMException.compilerError("Fresh constant used without a declared sort.", k);
                    }
                    Option<KLabel> lbl = m.freshFunctionFor().get(s.get());
                    if (!lbl.isDefined()) {
                        throw KEMException.compilerError("No fresh generator defined for sort " + s, k);
                    }
                    return KApply(lbl.get(), KApply(KLabel("_+Int_"), FRESH, KToken(offsets.get(k).toString(), Sorts.Int())));
                }
                return super.apply(k);
            }
        }.apply(term);
    }

    private K addFreshCell(K body) {
        if (freshVars.size() == 0) {
            return body;
        }
        KApply cellTerm = IncompleteCellUtils.make(KLabels.GENERATED_COUNTER_CELL, false, KRewrite(FRESH, KApply(KLabel("_+Int_"), FRESH, KToken(Integer.toString(freshVars.size()), Sorts.Int()))), false);
        return KApply(KLabels.CELLS, body, cellTerm);
    }

    private Context resolve(Context context) {
        reset();
        analyze(context.body());
        analyze(context.requires());
        finishAnalysis();
        return new Context(
                addFreshCell(transform(context.body())),
                transform(context.requires()),
                context.att());
    }

    private Production resolve(Production prod) {
        if (prod.klabel().isDefined() && prod.klabel().get().equals(KLabels.GENERATED_TOP_CELL)) {
            List<ProductionItem> pis = stream(prod.items()).collect(Collectors.toCollection(ArrayList::new));
            // expecting a production of the form <generatedTop> C1 C2 Cx.. </generatedTop>
            // insert the GeneratedCounterCell as the last cell
            // fixing the format gets resolved later in GeneratedTopFormat.java
            pis.add(prod.items().size() - 1, NonTerminal(Sorts.GeneratedCounterCell()));
            return Production(prod.klabel().get(), prod.sort(), immutable(pis), prod.att());
        }
        return prod;
    }

    private Sentence resolve(Sentence s) {
        if (s instanceof Rule) {
            return resolve((Rule) s);
        } else if (s instanceof Context) {
            return resolve((Context) s);
        } else if (s instanceof Production) {
            return resolve((Production) s);
        }
        return s;
    }

    public ResolveFreshConstants(Definition def, String manualTopCell, FileUtil files, boolean pedanticAttributes) {
        this.def = def;
        this.manualTopCell = manualTopCell;
        this.files = files;
        this.pedanticAttributes = pedanticAttributes;
    }

    public Module resolve(Module m) {
        this.m = m;
        Set<Sentence> sentences = map(this::resolve, m.localSentences());
        KToken counterCellLabel = KToken("generatedCounter", Sort("#CellName"));
        KApply freshCell = KApply(KLabel("#configCell"), counterCellLabel, KApply(KLabel("#cellPropertyListTerminator")), KToken("0", Sorts.Int()), counterCellLabel);

        java.util.Set<Sentence> counterSentences = new HashSet<>();
        counterSentences.add(Production(KLabel("getGeneratedCounterCell"), Sorts.GeneratedCounterCell(), Seq(Terminal("getGeneratedCounterCell"), Terminal("("), NonTerminal(Sorts.GeneratedTopCell()), Terminal(")")), Att.empty().add(Att.FUNCTION())));
        counterSentences.add(Rule(KRewrite(KApply(KLabel("getGeneratedCounterCell"), IncompleteCellUtils.make(KLabels.GENERATED_TOP_CELL, true, KVariable("Cell", Att.empty().add(Sort.class, Sorts.GeneratedCounterCell())), true)), KVariable("Cell", Att.empty().add(Sort.class, Sorts.GeneratedCounterCell()))), BooleanUtils.TRUE, BooleanUtils.TRUE));

        if (m.name().equals(def.mainModule().name())) {
            if (!m.definedKLabels().contains(KLabels.GENERATED_TOP_CELL)) {
                RuleGrammarGenerator gen = new RuleGrammarGenerator(def);
                ParseInModule mod = RuleGrammarGenerator.getCombinedGrammar(gen.getConfigGrammar(m), true, files);
                ConfigurationInfoFromModule configInfo = new ConfigurationInfoFromModule(m);
                Sort topCellSort;
                try {
                    topCellSort = configInfo.getRootCell();
                } catch (KEMException e) {
                    if (manualTopCell != null) {
                        topCellSort = Outer.parseSort(manualTopCell);
                    } else {
                      throw e;
                    }
                }
                KLabel topCellLabel = configInfo.getCellLabel(topCellSort);
                Production prod = m.productionsFor().apply(topCellLabel).head();
                KToken cellName = KToken(prod.att().get(Att.CELL_NAME()), Sort("#CellName"));

                KToken topCellToken = KToken(KLabels.GENERATED_TOP_CELL_NAME, Sort("#CellName"));
                K generatedTop = KApply(KLabel("#configCell"), topCellToken, KApply(KLabel("#cellPropertyListTerminator")), KApply(KLabels.CELLS, KApply(KLabel("#externalCell"), cellName), freshCell), topCellToken);
                Set<Sentence> newSentences = GenerateSentencesFromConfigDecl.gen(generatedTop, BooleanUtils.TRUE, Att.empty(), mod.getExtensionModule(), pedanticAttributes);
                sentences = (Set<Sentence>) sentences.$bar(newSentences);
                sentences = (Set<Sentence>) sentences.$bar(immutable(counterSentences));
            }
        }
        if (m.localKLabels().contains(KLabels.GENERATED_TOP_CELL)) {
            RuleGrammarGenerator gen = new RuleGrammarGenerator(def);
            ParseInModule mod = RuleGrammarGenerator.getCombinedGrammar(gen.getConfigGrammar(m), true, files);
            Set<Sentence> newSentences = GenerateSentencesFromConfigDecl.gen(freshCell, BooleanUtils.TRUE, Att.empty(), mod.getExtensionModule(), pedanticAttributes);
            sentences = (Set<Sentence>) sentences.$bar(newSentences);
            sentences = (Set<Sentence>) sentences.$bar(immutable(counterSentences));
        }
        if (sentences.equals(m.localSentences())) {
            return m;
        }
        return Module(m.name(), m.imports(), sentences, m.att());
    }
}

